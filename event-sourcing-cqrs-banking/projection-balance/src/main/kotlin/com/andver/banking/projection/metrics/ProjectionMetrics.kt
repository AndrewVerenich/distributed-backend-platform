package com.andver.banking.projection.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

interface ProjectionMetrics {
  fun recordConsumed(eventType: String)
  fun recordApplied(eventType: String, duration: Duration)
  fun recordSnapshot()
  fun recordError(eventType: String)

  companion object {
    val NOOP: ProjectionMetrics = object : ProjectionMetrics {
      override fun recordConsumed(eventType: String) = Unit
      override fun recordApplied(eventType: String, duration: Duration) = Unit
      override fun recordSnapshot() = Unit
      override fun recordError(eventType: String) = Unit
    }
  }
}

@Component
class MicrometerProjectionMetrics(
  private val registry: MeterRegistry,
) : ProjectionMetrics {

  private val consumed = ConcurrentHashMap<String, Counter>()
  private val applied = ConcurrentHashMap<String, Counter>()
  private val errors = ConcurrentHashMap<String, Counter>()
  private val timers = ConcurrentHashMap<String, Timer>()
  private val snapshots = Counter.builder("es_snapshots_created_total").register(registry)

  override fun recordConsumed(eventType: String) {
    consumed.computeIfAbsent(eventType) {
      Counter.builder("es_projection_consumed_total")
        .tag("eventType", eventType)
        .register(registry)
    }.increment()
  }

  override fun recordApplied(eventType: String, duration: Duration) {
    applied.computeIfAbsent(eventType) {
      Counter.builder("es_projection_applied_total")
        .tag("eventType", eventType)
        .register(registry)
    }.increment()
    timers.computeIfAbsent(eventType) {
      Timer.builder("es_projection_apply_seconds")
        .tag("eventType", eventType)
        .publishPercentiles(0.5, 0.95)
        .register(registry)
    }.record(duration)
  }

  override fun recordSnapshot() {
    snapshots.increment()
  }

  override fun recordError(eventType: String) {
    errors.computeIfAbsent(eventType) {
      Counter.builder("es_projection_errors_total")
        .tag("eventType", eventType)
        .register(registry)
    }.increment()
  }
}
