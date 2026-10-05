package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.metrics.MicrometerRateLimitMetrics
import com.andver.ratelimit.model.KeyStrategy
import com.andver.ratelimit.model.ResolvedRule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Real Redis via `docker run`. Testcontainers' docker-java client is unhappy on newer engines.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisRateLimiterIntegrationTest {

  private lateinit var limiter: RedisRateLimiter
  private lateinit var registry: SimpleMeterRegistry
  private lateinit var redisTemplate: ReactiveStringRedisTemplate
  private lateinit var factory: LettuceConnectionFactory

  private var containerId: String? = null
  private var redisPort: Int = 0

  @BeforeAll
  fun startRedis() {
    redisPort = freePort()
    val name = "server-rate-limiter-it-$redisPort"
    ProcessBuilder("docker", "rm", "-f", name).start().waitFor(30, TimeUnit.SECONDS)
    val started = ProcessBuilder(
      "docker", "run", "-d", "--rm",
      "--name", name,
      "-p", "$redisPort:6379",
      "redis:7.2-alpine",
    ).inheritIO().start().waitFor(60, TimeUnit.SECONDS)
    if (!started) {
      throw IllegalStateException("docker run did not finish")
    }
    containerId = name
    waitForRedis(redisPort)

    factory = LettuceConnectionFactory("127.0.0.1", redisPort)
    factory.afterPropertiesSet()
    val serializer = StringRedisSerializer()
    val context = RedisSerializationContext.newSerializationContext<String, String>(serializer).build()
    redisTemplate = ReactiveStringRedisTemplate(factory, context)
    registry = SimpleMeterRegistry()
    limiter = RedisRateLimiter(redisTemplate, "it", MicrometerRateLimitMetrics(registry))
  }

  @AfterAll
  fun stopRedis() {
    containerId?.let { id ->
      ProcessBuilder("docker", "rm", "-f", id).start().waitFor(30, TimeUnit.SECONDS)
    }
    if (this::factory.isInitialized) {
      factory.destroy()
    }
  }

  @BeforeEach
  fun flush() {
    redisTemplate.execute<String> { connection ->
      connection.serverCommands().flushAll().then(Mono.empty())
    }.then().block()
    registry.clear()
  }

  @Test
  fun `fixed window allows exactly the limit and resets with the next window`() {
    val rule = rule(RateLimitAlgorithm.FIXED_WINDOW, limit = 3, window = Duration.ofSeconds(1))

    val first = (1..3).map { limiter.check(rule, "alice").block()!! }
    assertTrue(first.all { it.allowed })
    assertEquals(listOf(2L, 1L, 0L), first.map { it.remaining })

    val rejected = limiter.check(rule, "alice").block()!!
    assertFalse(rejected.allowed)
    assertTrue(rejected.retryAfter.toMillis() > 0)

    Thread.sleep(1200)
    val again = limiter.check(rule, "alice").block()!!
    assertTrue(again.allowed)
    assertEquals(2L, again.remaining)
  }

  @Test
  fun `sliding log keeps the limit inside the rolling window`() {
    val rule = rule(RateLimitAlgorithm.SLIDING_WINDOW_LOG, limit = 3, window = Duration.ofSeconds(1))
    repeat(3) {
      assertTrue(limiter.check(rule, "alice").block()!!.allowed)
    }
    assertFalse(limiter.check(rule, "alice").block()!!.allowed)

    Thread.sleep(1200)
    assertTrue(limiter.check(rule, "alice").block()!!.allowed)
  }

  @Test
  fun `sliding counter matches the limit while the previous window is empty`() {
    val rule = rule(RateLimitAlgorithm.SLIDING_WINDOW_COUNTER, limit = 4, window = Duration.ofSeconds(30))
    val decisions = (1..8).map { limiter.check(rule, "alice").block()!! }
    assertEquals(4, decisions.count { it.allowed })
    assertEquals(0L, decisions.last().remaining)
  }

  @Test
  fun `token bucket grants the burst and then refills`() {
    val rule = rule(
      algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
      limit = 10,
      window = Duration.ofSeconds(1),
      burst = 4,
    )
    val burst = (1..6).map { limiter.check(rule, "alice").block()!! }
    assertEquals(4, burst.count { it.allowed })
    assertFalse(burst.last().allowed)
    assertTrue(burst.last().retryAfter.toMillis() > 0)

    Thread.sleep(1100)
    val refilled = (1..6).map { limiter.check(rule, "alice").block()!! }
    assertEquals(4, refilled.count { it.allowed })
  }

  @Test
  fun `parallel checks never hand out more than the limit`() {
    val rule = rule(RateLimitAlgorithm.FIXED_WINDOW, limit = 5, window = Duration.ofSeconds(30))
    val allowed = Flux.range(1, 40)
      .flatMap({ limiter.check(rule, "shared") }, 40)
      .filter { it.allowed }
      .count()
      .block()
    assertEquals(5L, requireNotNull(allowed))
  }

  @Test
  fun `identities and algorithms do not share a quota`() {
    val fixed = rule(RateLimitAlgorithm.FIXED_WINDOW, limit = 1, window = Duration.ofSeconds(30))
    val bucket = fixed.copy(algorithm = RateLimitAlgorithm.TOKEN_BUCKET, burst = 1)

    assertTrue(limiter.check(fixed, "alice").block()!!.allowed)
    assertFalse(limiter.check(fixed, "alice").block()!!.allowed)
    assertTrue(limiter.check(fixed, "bob").block()!!.allowed)
    assertTrue(limiter.check(bucket, "alice").block()!!.allowed)

    val allowed = registry.find("rl_requests")
      .tags("result", "allowed", "algorithm", "fixed-window")
      .counter()
    assertEquals(2.0, allowed?.count())
  }

  private fun rule(
    algorithm: RateLimitAlgorithm,
    limit: Long,
    window: Duration,
    burst: Long = limit,
  ) = ResolvedRule(
    name = "catalog",
    pathPrefixes = listOf("/api/catalog"),
    algorithm = algorithm,
    limit = limit,
    window = window,
    burst = burst,
    keyStrategy = KeyStrategy.HEADER,
    keyHeader = "X-User-Id",
  )

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
