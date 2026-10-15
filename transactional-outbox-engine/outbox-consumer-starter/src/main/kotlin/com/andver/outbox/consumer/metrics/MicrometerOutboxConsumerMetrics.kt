package com.andver.outbox.consumer.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class MicrometerOutboxConsumerMetrics(
  private val registry: MeterRegistry,
) : OutboxConsumerMetrics {

  private val consumed = ConcurrentHashMap<String, Counter>()
  private val missing = ConcurrentHashMap<String, Counter>()
  private val processed = ConcurrentHashMap<String, Counter>()
  private val timers = ConcurrentHashMap<String, Timer>()

  override fun recordConsumed(eventType: String) {
    consumed.computeIfAbsent(eventType) {
      Counter.builder("outbox_consumed_total")
        .tag("type", eventType)
        .register(registry)
    }.increment()
  }

  override fun recordHandlerMissing(eventType: String) {
    missing.computeIfAbsent(eventType) {
      Counter.builder("outbox_handler_missing_total")
        .tag("type", eventType)
        .register(registry)
    }.increment()
  }

  override fun recordProcessed(eventType: String, result: String) {
    processed.computeIfAbsent("$eventType/$result") {
      Counter.builder("outbox_processed_total")
        .tag("type", eventType)
        .tag("result", result)
        .description("processed | skipped (duplicate) | failed")
        .register(registry)
    }.increment()
  }

  override fun recordProcessDuration(eventType: String, duration: Duration) {
    timers.computeIfAbsent(eventType) {
      Timer.builder("outbox_process_seconds")
        .tag("type", eventType)
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
    }.record(duration)
  }
}
