package com.andver.clientratelimit.filter

import com.andver.clientratelimit.ClientRateLimiterProperties
import com.andver.clientratelimit.budget.ClientBudgetExceededException
import com.andver.clientratelimit.budget.CooldownStore
import com.andver.clientratelimit.budget.SharedBudgetGate
import com.andver.clientratelimit.headers.RateLimitHeaderParser
import com.andver.clientratelimit.metrics.ClientRateLimitMetrics
import com.andver.clientratelimit.rules.ClientRateLimitRuleSet
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.time.Duration

class WebClientClientRateLimitFilter(
  private val properties: ClientRateLimiterProperties,
  private val rules: ClientRateLimitRuleSet,
  private val budget: SharedBudgetGate,
  private val cooldown: CooldownStore,
  private val metrics: ClientRateLimitMetrics,
) : ExchangeFilterFunction {

  private val log = LoggerFactory.getLogger(WebClientClientRateLimitFilter::class.java)

  override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
    if (!properties.enabled) {
      return next.exchange(request)
    }
    val rule = rules.match(request.url().path) ?: return next.exchange(request)

    return budget.acquire(rule)
      .onErrorMap({ it !is ClientBudgetExceededException }) { error ->
        metrics.redisError(rule.name)
        error
      }
      .onErrorResume(ClientBudgetExceededException::class.java) { error ->
        metrics.budgetExceeded(rule.name)
        Mono.error(error)
      }
      .then(Mono.defer { exchangeOnce(request, next, rule.name, isRetry = false) })
  }

  private fun exchangeOnce(
    request: ClientRequest,
    next: ExchangeFunction,
    ruleName: String,
    isRetry: Boolean,
  ): Mono<ClientResponse> =
    next.exchange(request)
      .flatMap { response ->
        if (!isTooManyRequests(response.statusCode())) {
          if (isRetry) {
            metrics.recovered(ruleName)
          } else {
            metrics.success(ruleName)
          }
          return@flatMap Mono.just(response)
        }

        metrics.rateLimited(ruleName)
        if (isRetry) {
          metrics.failFast(ruleName)
          return@flatMap Mono.just(response)
        }

        val parsed = runCatching { RateLimitHeaderParser.parse(response.headers().asHttpHeaders()) }
          .onFailure {
            metrics.headerParseError()
            log.debug("failed to parse rate-limit headers rule={}", ruleName, it)
          }
          .getOrNull()

        val wait = parsed?.resolveWait()
        if (wait == null) {
          metrics.failFast(ruleName)
          log.info("429 without usable reset headers rule={} -> fail-fast", ruleName)
          return@flatMap Mono.just(response)
        }

        val clamped = clamp(wait)
        if (clamped.isZero) {
          // Reset already passed; retry immediately once.
          return@flatMap retryAfterCooldown(request, next, ruleName, Duration.ZERO)
        }

        log.info("429 rule={} waiting {} before single retry", ruleName, clamped)
        metrics.recordWait(ruleName, "header", clamped)
        cooldown.set(ruleName, clamped)
          .onErrorResume { error ->
            metrics.redisError(ruleName)
            Mono.error(error)
          }
          .then(retryAfterCooldown(request, next, ruleName, clamped))
      }

  private fun retryAfterCooldown(
    request: ClientRequest,
    next: ExchangeFunction,
    ruleName: String,
    wait: Duration,
  ): Mono<ClientResponse> {
    val delay = if (wait.isZero) Mono.empty() else Mono.delay(wait).then()
    val rule = rules.match(request.url().path)
      ?: return delay.then(exchangeOnce(request, next, ruleName, isRetry = true))

    return delay
      .then(budget.acquire(rule))
      .onErrorResume(ClientBudgetExceededException::class.java) { error ->
        metrics.budgetExceeded(rule.name)
        Mono.error(error)
      }
      .then(Mono.defer { exchangeOnce(request, next, ruleName, isRetry = true) })
  }

  private fun clamp(wait: Duration): Duration {
    val max = properties.maxWait
    return when {
      wait.isNegative -> Duration.ZERO
      wait > max -> max
      else -> wait
    }
  }

  private fun isTooManyRequests(status: HttpStatusCode): Boolean = status.value() == 429
}
