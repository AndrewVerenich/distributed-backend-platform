package com.andver.ratelimit.key

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.KeyStrategy
import com.andver.ratelimit.model.ResolvedRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import java.net.InetSocketAddress
import java.time.Duration

class RateLimitKeyResolverTest {

  private val resolver = RateLimitKeyResolver(fallbackToIp = true)
  private val headerRule = rule(KeyStrategy.HEADER)
  private val ipRule = rule(KeyStrategy.IP)

  @Test
  fun `header strategy reads the configured header`() {
    val request = MockServerHttpRequest.get("/api/catalog/1")
      .header("X-User-Id", "alice")
      .remoteAddress(InetSocketAddress("10.0.0.8", 4242))
      .build()

    assertEquals("alice", resolver.resolve(request, headerRule))
  }

  @Test
  fun `missing header falls back to the remote address`() {
    val request = MockServerHttpRequest.get("/api/catalog/1")
      .remoteAddress(InetSocketAddress("10.0.0.8", 4242))
      .build()

    assertEquals("10.0.0.8", resolver.resolve(request, headerRule))
  }

  @Test
  fun `ip strategy ignores the user header`() {
    val request = MockServerHttpRequest.get("/api/checkout")
      .header("X-User-Id", "alice")
      .remoteAddress(InetSocketAddress("10.1.1.1", 9))
      .build()

    assertEquals("10.1.1.1", resolver.resolve(request, ipRule))
  }

  @Test
  fun `unsafe characters are stripped from the redis key`() {
    assertEquals("alice_bob", RateLimitKeyResolver.sanitize("alice bob"))
    assertEquals("anonymous", RateLimitKeyResolver.sanitize("   "))
    assertEquals("a".repeat(128), RateLimitKeyResolver.sanitize("a".repeat(200)))
  }

  private fun rule(strategy: KeyStrategy) = ResolvedRule(
    name = "catalog",
    pathPrefixes = listOf("/api"),
    algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    limit = 10,
    window = Duration.ofSeconds(1),
    burst = 10,
    keyStrategy = strategy,
    keyHeader = "X-User-Id",
  )
}
