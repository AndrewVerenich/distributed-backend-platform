package com.andver.geomatching.geo

import com.andver.geomatching.GeoMatchingProperties
import org.junit.jupiter.api.AfterAll
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
import reactor.test.StepVerifier
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisGeoIndexIntegrationTest {

  private lateinit var index: RedisGeoIndex
  private lateinit var redisTemplate: ReactiveStringRedisTemplate
  private lateinit var factory: LettuceConnectionFactory
  private var containerId: String? = null
  private var redisPort: Int = 0

  @BeforeAll
  fun startRedis() {
    redisPort = ServerSocket(0).use { it.localPort }
    val name = "geo-it-$redisPort"
    ProcessBuilder(
      "docker", "run", "-d", "--rm",
      "--name", name,
      "-p", "$redisPort:6379",
      "redis:7.2-alpine",
    ).inheritIO().start().waitFor(60, TimeUnit.SECONDS)
    containerId = name
    waitForRedis(redisPort)

    factory = LettuceConnectionFactory("127.0.0.1", redisPort)
    factory.afterPropertiesSet()
    val serializer = StringRedisSerializer()
    val context = RedisSerializationContext.newSerializationContext<String, String>(serializer).build()
    redisTemplate = ReactiveStringRedisTemplate(factory, context)
    index = RedisGeoIndex(redisTemplate, GeoMatchingProperties(geoKey = "geo:test"))
  }

  @AfterAll
  fun stopRedis() {
    containerId?.let { ProcessBuilder("docker", "rm", "-f", it).start().waitFor(30, TimeUnit.SECONDS) }
    if (this::factory.isInitialized) factory.destroy()
  }

  @BeforeEach
  fun flush() {
    redisTemplate.execute<String> { connection ->
      connection.serverCommands().flushAll().then(Mono.empty())
    }.then().block()
  }

  @Test
  fun `search returns nearest members with distance`() {
    index.add("near", 27.5615, 53.9023).block()
    index.add("far", 27.60, 53.95).block()

    StepVerifier.create(index.search(27.5615, 53.9023, 3000.0, 10))
      .assertNext { list ->
        assert(list.first().providerId == "near")
        assert(list.first().distanceM < 50)
      }
      .verifyComplete()
  }

  private fun waitForRedis(port: Int) {
    val deadline = System.currentTimeMillis() + 30_000
    var last: Exception? = null
    while (System.currentTimeMillis() < deadline) {
      try {
        val probe = LettuceConnectionFactory("127.0.0.1", port)
        probe.afterPropertiesSet()
        ReactiveStringRedisTemplate(probe).opsForValue().get("ping").block(Duration.ofSeconds(2))
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
