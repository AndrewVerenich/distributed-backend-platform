package com.andver.ratelimit.metrics

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.RateLimitDecision
import java.time.Duration

object NoopRateLimitMetrics : RateLimitMetrics {
  override fun record(decision: RateLimitDecision, latency: Duration) = Unit

  override fun redisError(rule: String, algorithm: RateLimitAlgorithm, shard: String) = Unit
}
