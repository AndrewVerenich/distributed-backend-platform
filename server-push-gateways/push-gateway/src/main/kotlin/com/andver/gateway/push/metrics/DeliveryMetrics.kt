package com.andver.gateway.push.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface DeliveryMetrics {
  fun incrementSse(): Int
  fun decrementSse(): Int
  fun sseCount(): Int
  fun incrementPoll(): Int
  fun decrementPoll(): Int
  fun pollCount(): Int
  fun totalConnections(): Int
  fun recordDelivered(transport: String)
  fun recordReplayed(transport: String, reason: String)
  fun recordDeliveryLatency(transport: String, latency: Duration)
  fun recordReconnect(transport: String)
  fun recordRejected(reason: String)
  fun recordDrained(count: Int)
  fun recordDrainDuration(duration: Duration)
  fun recordRedisMessage()
  fun recordWaitersWoken(count: Int)
  fun recordBenchmark(transport: String, p50Ms: Double, p99Ms: Double)
}

@Component
class DefaultDeliveryMetrics(
  private val registry: MeterRegistry,
) : DeliveryMetrics {
  private val activeSse = AtomicInteger(0)
  private val pendingPolls = AtomicInteger(0)
  private val benchmarkP50 = ConcurrentHashMap<String, AtomicReference<Double>>()
  private val benchmarkP99 = ConcurrentHashMap<String, AtomicReference<Double>>()

  init {
    Gauge.builder("push_active_sse_connections", activeSse) { it.get().toDouble() }
      .description("Active SSE connections on this instance")
      .register(registry)
    Gauge.builder("push_pending_long_poll_requests", pendingPolls) { it.get().toDouble() }
      .description("Pending long-poll requests on this instance")
      .register(registry)
  }

  override fun incrementSse() = activeSse.incrementAndGet()
  override fun decrementSse() = activeSse.decrementAndGet()
  override fun sseCount(): Int = activeSse.get()

  override fun incrementPoll() = pendingPolls.incrementAndGet()
  override fun decrementPoll() = pendingPolls.decrementAndGet()
  override fun pollCount(): Int = pendingPolls.get()

  override fun totalConnections(): Int = activeSse.get() + pendingPolls.get()

  override fun recordDelivered(transport: String) {
    Counter.builder("push_events_delivered_total")
      .tag("transport", transport)
      .register(registry)
      .increment()
  }

  override fun recordReplayed(transport: String, reason: String) {
    Counter.builder("push_events_replayed_total")
      .tag("transport", transport)
      .tag("reason", reason)
      .register(registry)
      .increment()
  }

  override fun recordDeliveryLatency(transport: String, latency: Duration) {
    Timer.builder("push_delivery_latency_seconds")
      .tag("transport", transport)
      .publishPercentileHistogram()
      .register(registry)
      .record(latency)
  }

  override fun recordReconnect(transport: String) {
    Counter.builder("push_reconnect_total")
      .tag("transport", transport)
      .register(registry)
      .increment()
  }

  override fun recordRejected(reason: String) {
    Counter.builder("push_connections_rejected_total")
      .tag("reason", reason)
      .register(registry)
      .increment()
  }

  override fun recordDrained(count: Int) {
    Counter.builder("push_connections_drained_total")
      .register(registry)
      .increment(count.toDouble())
  }

  override fun recordDrainDuration(duration: Duration) {
    Timer.builder("push_drain_duration_seconds")
      .register(registry)
      .record(duration)
  }

  override fun recordRedisMessage() {
    Counter.builder("push_redis_messages_received_total")
      .register(registry)
      .increment()
  }

  override fun recordWaitersWoken(count: Int) {
    Counter.builder("push_local_waiters_woken_total")
      .tag("transport", "poll")
      .register(registry)
      .increment(count.toDouble())
  }

  override fun recordBenchmark(transport: String, p50Ms: Double, p99Ms: Double) {
    setGauge(
      "push_benchmark_last_p50_ms",
      transport,
      benchmarkP50,
      p50Ms,
      "Last benchmark p50 latency in ms",
    )
    setGauge(
      "push_benchmark_last_p99_ms",
      transport,
      benchmarkP99,
      p99Ms,
      "Last benchmark p99 latency in ms",
    )
  }

  private fun setGauge(
    metricName: String,
    transport: String,
    storage: ConcurrentHashMap<String, AtomicReference<Double>>,
    value: Double,
    description: String,
  ) {
    val ref = storage.computeIfAbsent(transport) { key ->
      val atomic = AtomicReference(value)
      Gauge.builder(metricName) { atomic.get() }
        .tag("transport", key)
        .description(description)
        .register(registry)
      atomic
    }
    ref.set(value)
  }
}
