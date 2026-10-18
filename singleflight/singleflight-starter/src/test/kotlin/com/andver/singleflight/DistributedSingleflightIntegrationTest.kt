package com.andver.singleflight

import com.andver.singleflight.internal.FlightCodec
import com.andver.singleflight.metrics.NoopSingleflightMetrics
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Redis via `docker run`. Testcontainers' docker-java client is unhappy on newer engines.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedSingleflightIntegrationTest {

  private lateinit var redisTemplate: ReactiveStringRedisTemplate
  private lateinit var factory: LettuceConnectionFactory
  private lateinit var codec: FlightCodec

  private var containerId: String? = null
  private var redisPort: Int = 0

  @BeforeAll
  fun startRedis() {
    redisPort = freePort()
    val name = "singleflight-it-$redisPort"
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
    codec = FlightCodec(jacksonObjectMapper())
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
  }

  @Test
  fun `distributed coalesces concurrent callers to one upstream load`() {
    val a = distributed("ns-a")
    val b = distributed("ns-a")
    val loads = AtomicInteger(0)

    val loader = {
      Mono.delay(Duration.ofMillis(120))
        .map {
          loads.incrementAndGet()
          "product-1"
        }
    }

    val outcomes = Flux.merge(
      Flux.range(1, 25).flatMap({ a.execute("hot", String::class.java, loader) }, 25),
      Flux.range(1, 25).flatMap({ b.execute("hot", String::class.java, loader) }, 25),
    )
      .collectList()
      .block(Duration.ofSeconds(10))!!

    assertEquals(50, outcomes.size)
    assertTrue(outcomes.all { it.value == "product-1" })
    assertEquals(1, loads.get())
    assertEquals(1, outcomes.count { it.role == SingleflightRole.LEADER })
    assertEquals(49, outcomes.count { it.role == SingleflightRole.WAITER })
  }

  @Test
  fun `different keys do not coalesce`() {
    val sf = distributed("ns-b")
    val loads = AtomicInteger(0)
    val loader = {
      Mono.fromCallable {
        loads.incrementAndGet()
        "v"
      }
    }

    Flux.range(1, 20)
      .flatMap { i -> sf.execute("key-$i", String::class.java, loader) }
      .then()
      .block(Duration.ofSeconds(5))

    assertEquals(20, loads.get())
  }

  private fun distributed(namespace: String): DistributedSingleflight =
    DistributedSingleflight(
      redisTemplate,
      SingleflightProperties(
        mode = SingleflightMode.DISTRIBUTED,
        namespace = namespace,
        waiterTimeout = Duration.ofSeconds(5),
        distributed = SingleflightProperties.Distributed(
          lease = Duration.ofSeconds(5),
          resultTtl = Duration.ofSeconds(10),
          pollInterval = Duration.ofMillis(20),
        ),
      ),
      NoopSingleflightMetrics,
      codec,
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
