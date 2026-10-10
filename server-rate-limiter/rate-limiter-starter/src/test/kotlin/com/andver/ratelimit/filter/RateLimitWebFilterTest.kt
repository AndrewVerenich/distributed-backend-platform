package com.andver.ratelimit.filter

import com.andver.ratelimit.RateLimiterProperties
import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.key.RateLimitKeyResolver
import com.andver.ratelimit.metrics.NoopRateLimitMetrics
import com.andver.ratelimit.model.FailureMode
import com.andver.ratelimit.model.RateLimitDecision
import com.andver.ratelimit.redis.RateLimiter
import com.andver.ratelimit.rules.RateLimitRuleSet
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant

class RateLimitWebFilterTest {

  private val limiter = mockk<RateLimiter>()
  private val properties = RateLimiterProperties(
    algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    onRedisError = FailureMode.REJECT,
    rules = listOf(
      RateLimiterProperties.RuleProperties(
        name = "catalog",
        pathPrefixes = listOf("/api/catalog"),
        limit = 20,
        window = Duration.ofSeconds(1),
        burst = 20,
      ),
    ),
  )
  private val filter = RateLimitWebFilter(
    limiter = limiter,
    rules = RateLimitRuleSet.from(properties),
    keys = RateLimitKeyResolver(fallbackToIp = true),
    metrics = NoopRateLimitMetrics,
    properties = properties,
  )

  @Test
  fun `skips paths that match no rule`() {
    val exchange = exchange("/actuator/health")
    var continued = false
    val chain = WebFilterChain {
      continued = true
      Mono.empty()
    }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
    assertTrue(continued)
    verify(exactly = 0) { limiter.check(any(), any()) }
    assertNull(exchange.response.headers.getFirst(RateLimitHeaders.LIMIT))
  }

  @Test
  fun `writes quota headers and continues when the call is allowed`() {
    every { limiter.check(any(), "alice") } returns Mono.just(allowed())
    val exchange = exchange("/api/catalog/sku-1", "alice")
    var continued = false
    val chain = WebFilterChain {
      continued = true
      it.response.statusCode = HttpStatus.OK
      Mono.empty()
    }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()

    assertTrue(continued)
    assertEquals("20", exchange.response.headers.getFirst(RateLimitHeaders.LIMIT))
    assertEquals("19", exchange.response.headers.getFirst(RateLimitHeaders.REMAINING))
    assertEquals("token-bucket", exchange.response.headers.getFirst(RateLimitHeaders.POLICY))
    assertEquals("shard-1", exchange.response.headers.getFirst(RateLimitHeaders.SHARD))
    assertEquals("19", exchange.response.headers.getFirst(RateLimitHeaders.DRAFT_REMAINING))
    assertNull(exchange.response.headers.getFirst(RateLimitHeaders.RETRY_AFTER))
    assertEquals(allowed().rule, RateLimitWebFilter.decision(exchange)?.rule)
  }

  @Test
  fun `answers 429 with Retry-After when the quota is spent`() {
    val decision = allowed().copy(allowed = false, remaining = 0, retryAfter = Duration.ofMillis(1500))
    every { limiter.check(any(), "alice") } returns Mono.just(decision)
    val exchange = exchange("/api/catalog/sku-1", "alice")
    var continued = false
    val chain = WebFilterChain {
      continued = true
      Mono.empty()
    }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()

    assertEquals(false, continued)
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    assertEquals("2", exchange.response.headers.getFirst(RateLimitHeaders.RETRY_AFTER))
    assertEquals("0", exchange.response.headers.getFirst(RateLimitHeaders.REMAINING))
    val body = exchange.response.bodyAsString.block()
    assertTrue(body!!.contains("rate limit exceeded"))
    assertTrue(body.contains("\"retryAfterMs\":1500"))
  }

  @Test
  fun `redis failure follows the configured failure mode`() {
    every { limiter.check(any(), any()) } returns Mono.error(IllegalStateException("redis down"))
    val exchange = exchange("/api/catalog/sku-1", "alice")
    val chain = WebFilterChain { Mono.empty() }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    val body = exchange.response.bodyAsString.block()
    assertTrue(body!!.contains("rate limiter unavailable"))
  }

  @Test
  fun `retry-after rounds partial seconds up and reset is an absolute timestamp`() {
    assertEquals(0, RateLimitHeaders.secondsCeil(Duration.ZERO))
    assertEquals(1, RateLimitHeaders.secondsCeil(Duration.ofMillis(1)))
    assertEquals(2, RateLimitHeaders.secondsCeil(Duration.ofMillis(1001)))
    val now = Instant.parse("2026-10-04T00:00:00Z")
    val headers = org.springframework.http.HttpHeaders()
    RateLimitHeaders.write(headers, allowed(), now)
    assertEquals(now.plusSeconds(1).epochSecond.toString(), headers.getFirst(RateLimitHeaders.RESET))
    assertEquals("1", headers.getFirst(RateLimitHeaders.DRAFT_RESET))
    assertEquals("20;w=1;burst=20", headers.getFirst(RateLimitHeaders.DRAFT_POLICY))
  }

  private fun exchange(path: String, user: String? = null): MockServerWebExchange {
    val builder = MockServerHttpRequest.get(path)
    if (user != null) {
      builder.header("X-User-Id", user)
    }
    return MockServerWebExchange.from(builder.build())
  }

  private fun allowed() = RateLimitDecision(
    allowed = true,
    rule = "catalog",
    algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    limit = 20,
    remaining = 19,
    retryAfter = Duration.ZERO,
    resetAfter = Duration.ofSeconds(1),
    window = Duration.ofSeconds(1),
    burst = 20,
    shard = "shard-1",
  )
}
