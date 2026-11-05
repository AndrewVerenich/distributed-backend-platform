package com.andver.clientratelimit.filter

import com.andver.clientratelimit.ClientRateLimiterProperties
import com.andver.clientratelimit.budget.CooldownStore
import com.andver.clientratelimit.budget.SharedBudgetGate
import com.andver.clientratelimit.budget.TokenBucket
import com.andver.clientratelimit.headers.RateLimitHeaderNames
import com.andver.clientratelimit.metrics.NoopClientRateLimitMetrics
import com.andver.clientratelimit.model.ClientRateLimitRule
import com.andver.clientratelimit.model.TokenBucketDecision
import com.andver.clientratelimit.rules.ClientRateLimitRuleSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class WebClientClientRateLimitFilterTest {

  private val rule = ClientRateLimitRule(
    name = "catalog",
    pathPrefixes = listOf("/api/catalog"),
    limit = 10,
    window = Duration.ofSeconds(1),
    burst = 10,
  )

  @Test
  fun `retries once after 429 using RateLimit-Reset`() {
    val attempts = AtomicInteger()
    val cooldown = RecordingCooldown()
    val filter = filter(cooldown)
    val exchange = ExchangeFunction { _ ->
      val n = attempts.incrementAndGet()
      if (n == 1) {
        Mono.just(
          ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
            .header(RateLimitHeaderNames.DRAFT_RESET, "1")
            .body("limited")
            .build(),
        )
      } else {
        Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build())
      }
    }

    StepVerifier.withVirtualTime {
      filter.filter(request("/api/catalog"), exchange)
        .flatMap { it.bodyToMono(String::class.java) }
    }
      .thenAwait(Duration.ofSeconds(1))
      .expectNext("ok")
      .verifyComplete()

    assertEquals(2, attempts.get())
    assertEquals(Duration.ofSeconds(1), cooldown.lastWait)
  }

  @Test
  fun `fail-fast when 429 has no reset headers`() {
    val attempts = AtomicInteger()
    val filter = filter(RecordingCooldown())
    val exchange = ExchangeFunction { _ ->
      attempts.incrementAndGet()
      Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).body("limited").build())
    }

    StepVerifier.create(
      filter.filter(request("/api/catalog"), exchange)
        .map { it.statusCode().value() },
    )
      .expectNext(429)
      .verifyComplete()

    assertEquals(1, attempts.get())
  }

  @Test
  fun `bypasses when path has no rule`() {
    val attempts = AtomicInteger()
    val filter = filter(RecordingCooldown())
    val exchange = ExchangeFunction { _ ->
      attempts.incrementAndGet()
      Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build())
    }

    StepVerifier.create(
      filter.filter(request("/other"), exchange)
        .flatMap { it.bodyToMono(String::class.java) },
    )
      .expectNext("ok")
      .verifyComplete()

    assertEquals(1, attempts.get())
  }

  private fun filter(cooldown: CooldownStore): WebClientClientRateLimitFilter {
    val properties = ClientRateLimiterProperties(maxWait = Duration.ofSeconds(30))
    val bucket = object : TokenBucket {
      override fun tryAcquire(rule: ClientRateLimitRule): Mono<TokenBucketDecision> =
        Mono.just(
          TokenBucketDecision(
            allowed = true,
            limit = 10,
            remaining = 9,
            retryAfter = Duration.ZERO,
            resetAfter = Duration.ZERO,
          ),
        )
    }
    val budget = SharedBudgetGate(bucket, cooldown, NoopClientRateLimitMetrics, properties.maxWait)
    return WebClientClientRateLimitFilter(
      properties = properties,
      rules = ClientRateLimitRuleSet(listOf(rule)),
      budget = budget,
      cooldown = cooldown,
      metrics = NoopClientRateLimitMetrics,
    )
  }

  private fun request(path: String): ClientRequest =
    ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://upstream$path")).build()

  private class RecordingCooldown : CooldownStore {
    var lastWait: Duration? = null

    override fun remaining(ruleName: String): Mono<Duration> = Mono.just(Duration.ZERO)

    override fun set(ruleName: String, wait: Duration): Mono<Void> {
      lastWait = wait
      return Mono.empty()
    }
  }
}
