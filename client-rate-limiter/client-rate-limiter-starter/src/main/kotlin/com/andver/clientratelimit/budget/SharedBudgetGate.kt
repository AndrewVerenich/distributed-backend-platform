package com.andver.clientratelimit.budget

import com.andver.clientratelimit.model.ClientRateLimitRule
import com.andver.clientratelimit.metrics.ClientRateLimitMetrics
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * Shared Redis budget: respect post-429 cooldown, then acquire a token (delay+retry until
 * [maxWait] is exhausted).
 */
class SharedBudgetGate(
  private val tokenBucket: TokenBucket,
  private val cooldown: CooldownStore,
  private val metrics: ClientRateLimitMetrics,
  private val maxWait: Duration,
) {
  fun acquire(rule: ClientRateLimitRule): Mono<Void> {
    val deadline = Instant.now().plus(maxWait)
    return respectCooldown(rule, deadline)
      .then(acquireToken(rule, deadline))
  }

  private fun respectCooldown(rule: ClientRateLimitRule, deadline: Instant): Mono<Void> =
    cooldown.remaining(rule.name)
      .flatMap { remaining ->
        if (remaining.isZero || remaining.isNegative) {
          Mono.empty()
        } else {
          val wait = clampToDeadline(remaining, deadline)
            ?: return@flatMap Mono.error(ClientBudgetExceededException(rule.name, "cooldown exceeds max-wait"))
          metrics.localThrottled(rule.name)
          metrics.recordWait(rule.name, "cooldown", wait)
          Mono.delay(wait).then()
        }
      }

  private fun acquireToken(rule: ClientRateLimitRule, deadline: Instant): Mono<Void> =
    tokenBucket.tryAcquire(rule)
      .flatMap { decision ->
        if (decision.allowed) {
          Mono.empty()
        } else {
          val wait = clampToDeadline(decision.retryAfter.coerceAtLeast(Duration.ofMillis(1)), deadline)
            ?: return@flatMap Mono.error(ClientBudgetExceededException(rule.name, "token wait exceeds max-wait"))
          metrics.localThrottled(rule.name)
          metrics.recordWait(rule.name, "local", wait)
          Mono.delay(wait).then(acquireToken(rule, deadline))
        }
      }

  private fun clampToDeadline(wait: Duration, deadline: Instant): Duration? {
    val left = Duration.between(Instant.now(), deadline)
    if (left.isNegative || left.isZero) {
      return null
    }
    return if (wait > left) left else wait
  }
}

class ClientBudgetExceededException(
  val rule: String,
  message: String,
) : RuntimeException("client rate budget exceeded for rule=$rule: $message")
