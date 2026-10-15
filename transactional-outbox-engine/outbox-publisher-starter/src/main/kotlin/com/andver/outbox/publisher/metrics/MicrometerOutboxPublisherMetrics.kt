package com.andver.outbox.publisher.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class MicrometerOutboxPublisherMetrics(
  private val registry: MeterRegistry,
) : OutboxPublisherMetrics {

  private val publishCounters = ConcurrentHashMap<String, Counter>()
  private val publishTimers = ConcurrentHashMap<String, Timer>()

  override fun recordPublished(eventType: String) {
    publishCounters.computeIfAbsent(eventType) {
      Counter.builder("outbox_published_total")
        .tag("type", eventType)
        .description("Outbox rows inserted in the same TX as business write")
        .register(registry)
    }.increment()
  }

  override fun recordPublishDuration(eventType: String, duration: Duration) {
    publishTimers.computeIfAbsent(eventType) {
      Timer.builder("outbox_publish_seconds")
        .tag("type", eventType)
        .publishPercentiles(0.5, 0.95)
        .register(registry)
    }.record(duration)
  }
}
