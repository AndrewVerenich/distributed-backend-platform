package com.andver.clientratelimit.headers

import com.andver.clientratelimit.model.ParsedRateLimitHeaders
import org.springframework.http.HttpHeaders
import java.time.Duration

object RateLimitHeaderNames {
  const val DRAFT_LIMIT = "RateLimit-Limit"
  const val DRAFT_REMAINING = "RateLimit-Remaining"
  const val DRAFT_RESET = "RateLimit-Reset"
  const val X_RESET = "X-RateLimit-Reset"
  const val RETRY_AFTER = "Retry-After"
}

object RateLimitHeaderParser {
  fun parse(headers: HttpHeaders): ParsedRateLimitHeaders =
    ParsedRateLimitHeaders(
      limit = longOrNull(headers, RateLimitHeaderNames.DRAFT_LIMIT),
      remaining = longOrNull(headers, RateLimitHeaderNames.DRAFT_REMAINING),
      rateLimitResetDelta = deltaSeconds(headers.getFirst(RateLimitHeaderNames.DRAFT_RESET)),
      xRateLimitResetEpochSeconds = longOrNull(headers, RateLimitHeaderNames.X_RESET),
      retryAfter = deltaSeconds(headers.getFirst(RateLimitHeaderNames.RETRY_AFTER)),
    )

  private fun longOrNull(headers: HttpHeaders, name: String): Long? =
    headers.getFirst(name)?.trim()?.toLongOrNull()

  private fun deltaSeconds(raw: String?): Duration? {
    val value = raw?.trim()?.toLongOrNull() ?: return null
    if (value < 0) {
      return null
    }
    return Duration.ofSeconds(value)
  }
}
