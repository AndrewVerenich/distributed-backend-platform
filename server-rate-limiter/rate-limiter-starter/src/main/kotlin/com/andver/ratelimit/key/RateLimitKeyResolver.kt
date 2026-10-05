package com.andver.ratelimit.key

import com.andver.ratelimit.model.KeyStrategy
import com.andver.ratelimit.model.ResolvedRule
import org.springframework.http.server.reactive.ServerHttpRequest

class RateLimitKeyResolver(
  private val fallbackToIp: Boolean,
) {
  fun resolve(request: ServerHttpRequest, rule: ResolvedRule): String {
    val raw = when (rule.keyStrategy) {
      KeyStrategy.HEADER -> headerOrIp(request, rule.keyHeader)
      KeyStrategy.IP -> ip(request) ?: ANONYMOUS
    }
    return sanitize(raw)
  }

  private fun headerOrIp(request: ServerHttpRequest, header: String): String {
    val value = request.headers.getFirst(header)?.trim()
    if (!value.isNullOrEmpty()) {
      return value
    }
    if (fallbackToIp) {
      return ip(request) ?: ANONYMOUS
    }
    return ANONYMOUS
  }

  private fun ip(request: ServerHttpRequest): String? =
    request.remoteAddress?.address?.hostAddress?.takeIf { it.isNotBlank() }

  companion object {
    const val ANONYMOUS = "anonymous"

    fun sanitize(raw: String): String =
      raw.trim()
        .replace(Regex("[^A-Za-z0-9._@-]"), "_")
        .take(MAX_LENGTH)
        .ifBlank { ANONYMOUS }

    private const val MAX_LENGTH = 128
  }
}
