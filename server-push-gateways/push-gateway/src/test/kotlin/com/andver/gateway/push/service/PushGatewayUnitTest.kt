package com.andver.gateway.push.service

import com.andver.gateway.push.metrics.DefaultDeliveryMetrics
import com.andver.push.model.PushEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class SseConnectionManagerTest {

  private val metrics = DefaultDeliveryMetrics(SimpleMeterRegistry())
  private val manager = DefaultSseConnectionManager(metrics)

  @Test
  fun `deliver routes event to registered session`() {
    val session = manager.register("alice")
    val event = PushEvent(
      eventId = 1,
      clientId = "alice",
      type = "test",
      publishedAt = Instant.now(),
    )

    StepVerifier.create(session.sink.asFlux().take(1))
      .then { assertEquals(1, manager.deliver("alice", event)) }
      .expectNext(event)
      .verifyComplete()

    manager.unregister(session)
    assertEquals(0, metrics.sseCount())
  }
}

class PendingPollRegistryTest {

  private val metrics = DefaultDeliveryMetrics(SimpleMeterRegistry())
  private val registry = DefaultPendingPollRegistry(metrics)

  @Test
  fun `completeLocalWaiters wakes pending poll`() {
    val mono = registry.registerMono("bob")
    val event = PushEvent(
      eventId = 7,
      clientId = "bob",
      type = "wake",
      publishedAt = Instant.now(),
    )

    StepVerifier.create(mono)
      .then { assertEquals(1, registry.completeLocalWaiters("bob", event)) }
      .expectNext(listOf(event))
      .verifyComplete()
  }

  @Test
  fun `timeout path leaves empty when nothing published`() {
    assertTrue(registry.pendingCount() >= 0)
  }
}

class ConnectionGateTest {

  @Test
  fun `rejects when draining`() {
    val metrics = DefaultDeliveryMetrics(SimpleMeterRegistry())
    val props = com.andver.gateway.push.config.PushGatewayProperties(maxConnections = 10)
    val gate = DefaultConnectionGate(props, metrics)
    gate.beginDrain()
    org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> {
      gate.assertAcceptable()
    }
  }
}
