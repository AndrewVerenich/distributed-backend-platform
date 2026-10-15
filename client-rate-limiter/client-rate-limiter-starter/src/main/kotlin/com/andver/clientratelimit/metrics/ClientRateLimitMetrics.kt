package com.andver.clientratelimit.metrics

import java.time.Duration

interface ClientRateLimitMetrics {
  fun success(rule: String)
  fun rateLimited(rule: String)
  fun recovered(rule: String)
  fun failFast(rule: String)
  fun localThrottled(rule: String)
  fun recordWait(rule: String, reason: String, wait: Duration)
  fun headerParseError()
  fun redisError(rule: String)
  fun budgetExceeded(rule: String)
}

object NoopClientRateLimitMetrics : ClientRateLimitMetrics {
  override fun success(rule: String) = Unit
  override fun rateLimited(rule: String) = Unit
  override fun recovered(rule: String) = Unit
  override fun failFast(rule: String) = Unit
  override fun localThrottled(rule: String) = Unit
  override fun recordWait(rule: String, reason: String, wait: Duration) = Unit
  override fun headerParseError() = Unit
  override fun redisError(rule: String) = Unit
  override fun budgetExceeded(rule: String) = Unit
}
