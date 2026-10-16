package com.andver.webhook.worker

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DeliveryMetrics(registry: MeterRegistry) {
  private val succeeded = Counter.builder("webhook_deliveries_succeeded").register(registry)
  private val retry = Counter.builder("webhook_deliveries_retry").register(registry)
  private val dlq = Counter.builder("webhook_deliveries_dlq").register(registry)
  private val paused = Counter.builder("webhook_endpoint_paused").register(registry)
  private val skippedCap = Counter.builder("webhook_claim_skipped_inflight_cap").register(registry)
  private val latency = Timer.builder("webhook_outbound_latency").register(registry)

  fun succeeded() = succeeded.increment()
  fun retry() = retry.increment()
  fun dlq() = dlq.increment()
  fun paused() = paused.increment()
  fun skippedCap() = skippedCap.increment()
  fun recordLatency(ms: Long) = latency.record(ms, TimeUnit.MILLISECONDS)
}
