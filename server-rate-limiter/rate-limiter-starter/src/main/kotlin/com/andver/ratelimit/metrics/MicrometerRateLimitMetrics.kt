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
    increment(decision.rule, decision.algorithm, result, decision.shard)
    timer(decision.rule, decision.algorithm, result, decision.shard).record(latency)
  }

  override fun redisError(rule: String, algorithm: RateLimitAlgorithm, shard: String) {
    increment(rule, algorithm, "redis_error", shard)
  }

  private fun increment(rule: String, algorithm: RateLimitAlgorithm, result: String, shard: String) {
    registry.counter(
      "rl_requests",
      "rule", rule,
      "algorithm", algorithm.configName(),
      "result", result,
      "shard", shard,
    ).increment()
  }

  private fun timer(rule: String, algorithm: RateLimitAlgorithm, result: String, shard: String): Timer =
    Timer.builder("rl_check")
      .tag("rule", rule)
      .tag("algorithm", algorithm.configName())
      .tag("result", result)
      .tag("shard", shard)
      .publishPercentileHistogram()
      .register(registry)
}
