package com.andver.gateway.push.service

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.gateway.push.metrics.DefaultDeliveryMetrics
import com.andver.push.model.PushEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import reactor.test.StepVerifier
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant

/**
 * Requires Redis on localhost:6379 (docker compose in this project).
 * Run: docker compose up -d redis && ./gradlew :server-push-gateways:push-gateway:integrationTest
 */
@Tag("integration")
class RedisReplayAndFanoutIntegrationTest {

  companion object {
    private const val REDIS_HOST = "127.0.0.1"
    private const val REDIS_PORT = 6379

    @JvmStatic
    @BeforeAll
    fun requireRedis() {
      assumeTrue(isPortOpen(REDIS_HOST, REDIS_PORT), "Redis is not available on $REDIS_HOST:$REDIS_PORT")
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
      return try {
        Socket().use { socket ->
          socket.connect(InetSocketAddress(host, port), 500)
          true
        }
      } catch (_: Exception) {
        false
      }
    }
  }

  private fun objectMapper(): ObjectMapper =
    ObjectMapper()
      .registerModule(kotlinModule())
      .registerModule(JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  private fun connectionFactory(): LettuceConnectionFactory {
    val factory = LettuceConnectionFactory(REDIS_HOST, REDIS_PORT)
    factory.afterPropertiesSet()
    return factory
  }

  @Test
  fun `replay buffer returns events after cursor`() {
    val factory = connectionFactory()
    val stringTemplate = StringRedisTemplate(factory)
    val reactive = ReactiveStringRedisTemplate(factory)
    val mapper = objectMapper()
    val props = PushGatewayProperties()
    val replay = DefaultEventReplayService(props, reactive, mapper)
    val clientId = "carol-it-${System.nanoTime()}"

    val event = PushEvent(
      eventId = 42,
      clientId = clientId,
      type = "order.updated",
      payload = mapOf("x" to 1),
      publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
    )
    val key = "${props.replayPrefix}:$clientId"
    stringTemplate.delete(key)
    val json = mapper.writeValueAsString(event)
    stringTemplate.opsForList().leftPush(key, json)

    StepVerifier.create(replay.findSince(clientId, 40))
      .assertNext { list ->
        assertEquals(1, list.size)
        assertEquals(42, list[0].eventId)
      }
      .verifyComplete()

    stringTemplate.delete(key)
    factory.destroy()
  }

  @Test
  fun `pubsub message wakes local poll waiter`() {
    val factory = connectionFactory()
    val stringTemplate = StringRedisTemplate(factory)
    val reactive = ReactiveStringRedisTemplate(factory)
    val mapper = objectMapper()
    val props = PushGatewayProperties()
    val metrics = DefaultDeliveryMetrics(SimpleMeterRegistry())
    val channelService = DefaultClientChannelService(props, reactive, mapper, metrics)
    val pollRegistry = DefaultPendingPollRegistry(metrics)
    val sseManager = DefaultSseConnectionManager(metrics)
    val fanout = DefaultEventFanoutService(channelService, sseManager, pollRegistry, metrics)

    fanout.ensureSubscribed("dave-it")
    val waiter = pollRegistry.registerMono("dave-it")

    val event = PushEvent(
      eventId = 99,
      clientId = "dave-it",
      type = "ping",
      publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
    )

    Thread.sleep(400)
    stringTemplate.convertAndSend("${props.channelPrefix}:dave-it", mapper.writeValueAsString(event))

    StepVerifier.create(waiter.map { it.map { e -> e.eventId } })
      .expectNext(listOf(99L))
      .expectComplete()
      .verify(Duration.ofSeconds(5))

    fanout.drainSubscriptions()
    factory.destroy()
  }
}
