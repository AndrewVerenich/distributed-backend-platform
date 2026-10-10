package com.andver.ratelimit.filter

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.RateLimitDecision
import org.springframework.http.HttpHeaders
import java.time.Instant

object RateLimitHeaders {
  const val LIMIT = "X-RateLimit-Limit"
  const val REMAINING = "X-RateLimit-Remaining"
  const val RESET = "X-RateLimit-Reset"
  const val POLICY = "X-RateLimit-Policy"
  const val SHARD = "X-RateLimit-Shard"
  const val RETRY_AFTER = "Retry-After"
  const val DRAFT_LIMIT = "RateLimit-Limit"
  const val DRAFT_REMAINING = "RateLimit-Remaining"
  const val DRAFT_RESET = "RateLimit-Reset"
  const val DRAFT_POLICY = "RateLimit-Policy"

  fun write(headers: HttpHeaders, decision: RateLimitDecision, now: Instant = Instant.now()) {
    val resetEpoch = now.plus(decision.resetAfter).epochSecond
    val resetDelta = secondsCeil(decision.resetAfter)
    headers[LIMIT] = decision.limit.toString()
    headers[REMAINING] = decision.remaining.toString()
    headers[RESET] = resetEpoch.toString()
    headers[POLICY] = decision.algorithm.configName()
    headers[SHARD] = decision.shard
    headers[DRAFT_LIMIT] = decision.limit.toString()
    headers[DRAFT_REMAINING] = decision.remaining.toString()
    headers[DRAFT_RESET] = resetDelta.toString()
    headers[DRAFT_POLICY] = policy(decision)
    if (!decision.allowed) {
      val retry = secondsCeil(decision.retryAfter).coerceAtLeast(1)
      headers[RETRY_AFTER] = retry.toString()
    }
  }

  fun secondsCeil(duration: java.time.Duration): Long {
    val millis = duration.toMillis()
    if (millis <= 0) {
      return 0
    }
    return (millis + 999) / 1000
  }

  private fun policy(decision: RateLimitDecision): String {
    val windowSeconds = secondsCeil(decision.window).coerceAtLeast(1)
    val base = "${decision.limit};w=$windowSeconds"
    return if (decision.algorithm == RateLimitAlgorithm.TOKEN_BUCKET) {
      "$base;burst=${decision.burst}"
    } else {
      base
    }
  }
}
