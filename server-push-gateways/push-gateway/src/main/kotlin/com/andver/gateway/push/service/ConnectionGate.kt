package com.andver.gateway.push.service

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.gateway.push.metrics.DeliveryMetrics
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

interface ConnectionGate {
  fun isDraining(): Boolean
  fun beginDrain()
  fun assertAcceptable()
}

@Component
class DefaultConnectionGate(
  private val properties: PushGatewayProperties,
  private val metrics: DeliveryMetrics,
) : ConnectionGate {
  private val draining = AtomicBoolean(false)

  override fun isDraining(): Boolean = draining.get()

  override fun beginDrain() {
    draining.set(true)
  }

  override fun assertAcceptable() {
    if (draining.get()) {
      metrics.recordRejected("draining")
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gateway is draining")
    }
    if (metrics.totalConnections() >= properties.maxConnections) {
      metrics.recordRejected("saturation")
      throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Connection limit reached")
    }
  }
}
