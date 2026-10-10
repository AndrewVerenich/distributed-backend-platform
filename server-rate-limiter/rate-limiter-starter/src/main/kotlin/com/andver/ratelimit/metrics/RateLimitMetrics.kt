package com.andver.ratelimit.metrics

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.RateLimitDecision
import java.time.Duration

interface RateLimitMetrics {
  fun record(decision: RateLimitDecision, latency: Duration)

  fun redisError(rule: String, algorithm: RateLimitAlgorithm, shard: String)
}
