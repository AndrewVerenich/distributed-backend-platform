package com.andver.clientratelimit.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.TimeUnit

class MicrometerClientRateLimitMetrics(
  private val registry: MeterRegistry,
) : ClientRateLimitMetrics {

  override fun success(rule: String) = increment(rule, "success")

  override fun rateLimited(rule: String) = increment(rule, "rate_limited")

  override fun recovered(rule: String) = increment(rule, "recovered")

  override fun failFast(rule: String) = increment(rule, "fail_fast")

  override fun localThrottled(rule: String) {
    registry.counter("crl_local_throttled_total", "rule", rule).increment()
  }

  override fun recordWait(rule: String, reason: String, wait: Duration) {
    Timer.builder("crl_wait_seconds")
      .tag("rule", rule)
      .tag("reason", reason)
      .register(registry)
      .record(wait.toNanos(), TimeUnit.NANOSECONDS)
  }

  override fun headerParseError() {
    registry.counter("crl_header_parse_errors_total").increment()
  }

  override fun redisError(rule: String) {
    registry.counter("crl_redis_errors_total", "rule", rule).increment()
  }

  override fun budgetExceeded(rule: String) = increment(rule, "budget_exceeded")

  private fun increment(rule: String, result: String) {
    registry.counter("crl_requests_total", "rule", rule, "result", result).increment()
  }
}
