package com.andver.clientratelimit.model

import java.time.Duration

data class ClientRateLimitRule(
  val name: String,
  val pathPrefixes: List<String>,
  val limit: Long,
  val window: Duration,
  val burst: Long,
)

data class TokenBucketDecision(
  val allowed: Boolean,
  val limit: Long,
  val remaining: Long,
  val retryAfter: Duration,
  val resetAfter: Duration,
)

data class ParsedRateLimitHeaders(
  val limit: Long? = null,
  val remaining: Long? = null,
  val rateLimitResetDelta: Duration? = null,
  val xRateLimitResetEpochSeconds: Long? = null,
  val retryAfter: Duration? = null,
) {
  fun resolveWait(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Duration? {
    rateLimitResetDelta?.takeIf { !it.isNegative }?.let { return it }

    xRateLimitResetEpochSeconds?.let { epoch ->
      val deltaSeconds = (epoch - nowEpochSeconds).coerceAtLeast(0)
      return Duration.ofSeconds(deltaSeconds)
    }

    retryAfter?.takeIf { !it.isNegative }?.let { return it }
    return null
  }
}
