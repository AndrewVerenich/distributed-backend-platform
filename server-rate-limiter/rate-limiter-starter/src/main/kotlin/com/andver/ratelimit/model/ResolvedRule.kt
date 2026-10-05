package com.andver.ratelimit.model

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import java.time.Duration

data class ResolvedRule(
  val name: String,
  val pathPrefixes: List<String>,
  val algorithm: RateLimitAlgorithm,
  val limit: Long,
  val window: Duration,
  val burst: Long,
  val keyStrategy: KeyStrategy,
  val keyHeader: String,
)
