package com.andver.gateway.websocket.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class WebsocketMetrics(
  private val registry: MeterRegistry,
) {
  private val connections = AtomicLong(0)
  private val handshakes = ConcurrentHashMap<String, Counter>()
  private val messagesIn = Counter.builder("wsg_messages_in_total").register(registry)
  private val messagesOut = Counter.builder("wsg_messages_out_total").register(registry)
  private val deserializeErrors = Counter.builder("wsg_deserialize_errors_total").register(registry)

  init {
    registry.gauge("websocket.connections.total", connections) { it.get().toDouble() }
  }

  fun handshakeOk() {
    handshakes.computeIfAbsent("ok") {
      Counter.builder("wsg_handshake_total").tag("result", "ok").register(registry)
    }.increment()
  }

  fun handshakeUnauthorized() {
    handshakes.computeIfAbsent("unauthorized") {
      Counter.builder("wsg_handshake_total").tag("result", "unauthorized").register(registry)
    }.increment()
  }

  fun connectionOpened() {
    connections.incrementAndGet()
  }

  fun connectionClosed() {
    connections.decrementAndGet()
  }

  fun messageIn() {
    messagesIn.increment()
  }

  fun messageOut() {
    messagesOut.increment()
  }

  fun deserializeError() {
    deserializeErrors.increment()
  }
}
