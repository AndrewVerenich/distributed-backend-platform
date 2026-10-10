package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.metrics.MicrometerRateLimitMetrics
import com.andver.ratelimit.model.KeyStrategy
import com.andver.ratelimit.model.ResolvedRule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import reactor.core.publisher.Mono
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisShardingIntegrationTest {

  private lateinit var shard0: ReactiveStringRedisTemplate
  private lateinit var shard1: ReactiveStringRedisTemplate
  private lateinit var factory0: LettuceConnectionFactory
  private lateinit var factory1: LettuceConnectionFactory
  private lateinit var registry: RedisShardRegistry
  private lateinit var limiter: RedisRateLimiter
  private lateinit var meters: SimpleMeterRegistry

  private var container0: String? = null
  private var container1: String? = null
  private var port0: Int = 0
  private var port1: Int = 0

  @BeforeAll
  fun startRedis() {
    port0 = freePort()
    port1 = freePort()
    container0 = startContainer("server-rate-limiter-shard-it-0-$port0", port0)
    container1 = startContainer("server-rate-limiter-shard-it-1-$port1", port1)
    waitForRedis(port0)
    waitForRedis(port1)

    factory0 = factory(port0)
    factory1 = factory(port1)
    shard0 = template(factory0)
    shard1 = template(factory1)
    registry = RedisShardRegistry(
      listOf(
        RedisShard("shard-0", 0, shard0),
        RedisShard("shard-1", 1, shard1),
      ),
    )
    meters = SimpleMeterRegistry()
    limiter = RedisRateLimiter(registry, "it", MicrometerRateLimitMetrics(meters))
  }

  @AfterAll
  fun stopRedis() {
    container0?.let { ProcessBuilder("docker", "rm", "-f", it).start().waitFor(30, TimeUnit.SECONDS) }
    container1?.let { ProcessBuilder("docker", "rm", "-f", it).start().waitFor(30, TimeUnit.SECONDS) }
    if (this::factory0.isInitialized) factory0.destroy()
    if (this::factory1.isInitialized) factory1.destroy()
  }

  @BeforeEach
  fun flush() {
    flush(shard0)
    flush(shard1)
    meters.clear()
  }

  @Test
  fun `same identity always lands on one shard and keeps a single quota`() {
    val rule = rule()
    val identity = "sticky-user"
    val shard = registry.resolve(identity)

    val decisions = (1..5).map { limiter.check(rule, identity).block()!! }
    assertEquals(3, decisions.count { it.allowed })
    assertTrue(decisions.all { it.shard == shard.name })

    val other = if (shard.index == 0) shard1 else shard0
    assertTrue(scanCount(shard.redis, "it:*") >= 1)
    assertEquals(0L, scanCount(other, "it:*"))
  }

  @Test
  fun `different identities can spread across shards`() {
    val rule = rule()
    val byShard = (0 until 80)
      .map { "user-$it" }
      .map { identity -> limiter.check(rule, identity).block()!!.shard }
      .groupingBy { it }
      .eachCount()

    assertTrue(byShard.size >= 2) { "expected both shards, got $byShard" }
    assertNotEquals(0, byShard["shard-0"] ?: 0)
    assertNotEquals(0, byShard["shard-1"] ?: 0)

    val shard0Allowed = meters.find("rl_requests")
      .tags("shard", "shard-0", "result", "allowed")
      .counter()
      ?.count() ?: 0.0
    val shard1Allowed = meters.find("rl_requests")
      .tags("shard", "shard-1", "result", "allowed")
      .counter()
      ?.count() ?: 0.0
    assertTrue(shard0Allowed > 0)
    assertTrue(shard1Allowed > 0)
  }

  private fun rule() = ResolvedRule(
    name = "catalog",
    pathPrefixes = listOf("/api/catalog"),
    algorithm = RateLimitAlgorithm.FIXED_WINDOW,
    limit = 3,
    window = Duration.ofSeconds(30),
    burst = 3,
    keyStrategy = KeyStrategy.HEADER,
    keyHeader = "X-User-Id",
  )

  private fun startContainer(name: String, port: Int): String {
    ProcessBuilder("docker", "rm", "-f", name).start().waitFor(30, TimeUnit.SECONDS)
    val started = ProcessBuilder(
      "docker", "run", "-d", "--rm",
      "--name", name,
      "-p", "$port:6379",
      "redis:7.2-alpine",
    ).inheritIO().start().waitFor(60, TimeUnit.SECONDS)
    if (!started) {
      throw IllegalStateException("docker run did not finish for $name")
    }
    return name
  }

  private fun factory(port: Int): LettuceConnectionFactory =
    LettuceConnectionFactory("127.0.0.1", port).also { it.afterPropertiesSet() }

  private fun template(factory: LettuceConnectionFactory): ReactiveStringRedisTemplate {
    val serializer = StringRedisSerializer()
    val context = RedisSerializationContext.newSerializationContext<String, String>(serializer).build()
    return ReactiveStringRedisTemplate(factory, context)
  }

  private fun flush(redis: ReactiveStringRedisTemplate) {
    redis.execute<String> { connection ->
      connection.serverCommands().flushAll().then(Mono.empty())
    }.then().block()
  }

  private fun scanCount(redis: ReactiveStringRedisTemplate, pattern: String): Long =
    redis.keys(pattern).count().block() ?: 0L

  private fun freePort(): Int = ServerSocket(0).use { it.localPort }

  private fun waitForRedis(port: Int) {
    val deadline = System.currentTimeMillis() + 30_000
    var last: Exception? = null
    while (System.currentTimeMillis() < deadline) {
      try {
        val probe = LettuceConnectionFactory("127.0.0.1", port)
        probe.afterPropertiesSet()
        val template = ReactiveStringRedisTemplate(probe)
        template.opsForValue().get("ping").block(Duration.ofSeconds(2))
        probe.destroy()
        return
      } catch (ex: Exception) {
        last = ex
        Thread.sleep(300)
      }
    }
    throw IllegalStateException("Redis did not become ready on port $port", last)
  }
}
