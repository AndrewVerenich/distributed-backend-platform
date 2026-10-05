package com.andver.ratelimit.metrics

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.RateLimitDecision
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

class MicrometerRateLimitMetrics(
  private val registry: MeterRegistry,
) : RateLimitMetrics {

  override fun record(decision: RateLimitDecision, latency: Duration) {
    val result = if (decision.allowed) "allowed" else "rejected"
    increment(decision.rule, decision.algorithm, result)
    timer(decision.rule, decision.algorithm, result).record(latency)
  }

  override fun redisError(rule: String, algorithm: RateLimitAlgorithm) {
    increment(rule, algorithm, "redis_error")
  }

  private fun increment(rule: String, algorithm: RateLimitAlgorithm, result: String) {
    registry.counter(
      "rl_requests",
      "rule", rule,
      "algorithm", algorithm.configName(),
      "result", result,
    ).increment()
  }

  private fun timer(rule: String, algorithm: RateLimitAlgorithm, result: String): Timer =
    Timer.builder("rl_check")
      .tag("rule", rule)
      .tag("algorithm", algorithm.configName())
      .tag("result", result)
      .publishPercentileHistogram()
      .register(registry)
}
