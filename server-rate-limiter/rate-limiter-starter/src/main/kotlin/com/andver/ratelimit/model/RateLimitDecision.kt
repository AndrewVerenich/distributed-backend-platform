package com.andver.ratelimit.model

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import java.time.Duration

data class RateLimitDecision(
  val allowed: Boolean,
  val rule: String,
  val algorithm: RateLimitAlgorithm,
  val limit: Long,
  val remaining: Long,
  val retryAfter: Duration,
  val resetAfter: Duration,
  val window: Duration,
  val burst: Long,
  val unavailable: Boolean = false,
) {
  companion object {
    fun unavailable(rule: ResolvedRule, allow: Boolean): RateLimitDecision =
      RateLimitDecision(
        allowed = allow,
        rule = rule.name,
        algorithm = rule.algorithm,
        limit = rule.limit,
        remaining = if (allow) rule.limit else 0,
        retryAfter = if (allow) Duration.ZERO else Duration.ofSeconds(1),
        resetAfter = Duration.ofSeconds(1),
        window = rule.window,
        burst = rule.burst,
        unavailable = true,
      )
  }
}
