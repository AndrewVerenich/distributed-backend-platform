package com.andver.waitingroom.checkout.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CheckoutMetrics(
  private val registry: MeterRegistry,
) {
  fun success() = registry.counter("checkout_success").increment()
  fun error(reason: String) = registry.counter("checkout_error", "reason", reason).increment()
  fun recordLatency(duration: Duration) {
    Timer.builder("checkout_latency")
      .publishPercentileHistogram()
      .register(registry)
      .record(duration)
  }
}
