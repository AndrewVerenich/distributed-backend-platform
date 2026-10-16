package com.andver.webhook.core

import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class RetryPolicy(
  private val backoffSeconds: List<Long> = listOf(3, 8, 20),
  private val maxAttempts: Int = 5,
  private val jitterRatio: Double = 0.2,
) {
  fun nextAttemptAt(attemptCountAfterFailure: Int, now: Instant = Instant.now()): Instant? {
    if (attemptCountAfterFailure >= maxAttempts) return null
    val idx = (attemptCountAfterFailure - 1).coerceIn(0, backoffSeconds.lastIndex)
    val base = backoffSeconds[idx]
    val jitter = (base * jitterRatio * Random.nextDouble()).toLong()
    return now.plusSeconds(base + jitter)
  }

  fun classifyHttp(statusCode: Int?, error: Throwable?): DeliveryOutcome {
    if (error != null) {
      return DeliveryOutcome.Retryable(error.message ?: error.javaClass.simpleName)
    }
    val code = statusCode ?: return DeliveryOutcome.Retryable("empty status")
    return when {
      code in 200..299 -> DeliveryOutcome.Success
      code == 408 || code == 429 || code >= 500 -> DeliveryOutcome.Retryable("http $code")
      else -> DeliveryOutcome.NonRetryable(code, "http $code")
    }
  }

  fun exhausted(attemptCount: Int): Boolean = attemptCount >= maxAttempts

  fun backoffPreview(): List<Duration> = backoffSeconds.map { Duration.ofSeconds(it) }
}
