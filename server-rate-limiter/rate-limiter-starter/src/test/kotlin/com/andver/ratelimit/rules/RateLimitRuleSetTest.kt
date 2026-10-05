package com.andver.ratelimit.rules

import com.andver.ratelimit.RateLimiterProperties
import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.KeyStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimitRuleSetTest {

  private val properties = RateLimiterProperties(
    algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
    key = RateLimiterProperties.KeyProperties(strategy = KeyStrategy.HEADER, header = "X-User-Id"),
    filter = RateLimiterProperties.FilterProperties(excludePathPrefixes = listOf("/actuator")),
    rules = listOf(
      RateLimiterProperties.RuleProperties(
        name = "api",
        pathPrefixes = listOf("/api"),
        limit = 100,
        window = Duration.ofSeconds(1),
      ),
      RateLimiterProperties.RuleProperties(
        name = "catalog",
        pathPrefixes = listOf("/api/catalog"),
        algorithm = RateLimitAlgorithm.FIXED_WINDOW,
        limit = 20,
        window = Duration.ofSeconds(1),
        burst = 40,
      ),
      RateLimiterProperties.RuleProperties(
        name = "checkout",
        pathPrefixes = listOf("/api/checkout"),
        limit = 5,
        window = Duration.ofSeconds(1),
        burst = 20,
        keyStrategy = KeyStrategy.IP,
      ),
    ),
  )

  private val rules = RateLimitRuleSet.from(properties)

  @Test
  fun `longest prefix wins over an earlier shorter rule`() {
    val match = rules.match("/api/catalog/sku-1")
    assertEquals("catalog", match?.name)
    assertEquals(RateLimitAlgorithm.FIXED_WINDOW, match?.algorithm)
    assertEquals(40, match?.burst)
  }

  @Test
  fun `falls back to the shorter prefix`() {
    val match = rules.match("/api/search")
    assertEquals("api", match?.name)
    assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, match?.algorithm)
    assertEquals(100, match?.burst)
  }

  @Test
  fun `checkout keeps its own key strategy`() {
    val match = rules.match("/api/checkout")
    assertEquals(KeyStrategy.IP, match?.keyStrategy)
    assertEquals(5, match?.limit)
    assertEquals(20, match?.burst)
  }

  @Test
  fun `actuator is excluded and unknown paths are not limited`() {
    assertNull(rules.match("/actuator/prometheus"))
    assertNull(rules.match("/health"))
  }

  @Test
  fun `equal prefixes keep the earlier rule`() {
    val tied = RateLimitRuleSet.from(
      properties.copy(
        rules = listOf(
          RateLimiterProperties.RuleProperties(
            name = "first",
            pathPrefixes = listOf("/api"),
            limit = 1,
            window = Duration.ofSeconds(1),
          ),
          RateLimiterProperties.RuleProperties(
            name = "second",
            pathPrefixes = listOf("/api"),
            limit = 2,
            window = Duration.ofSeconds(1),
          ),
        ),
      ),
    )
    assertEquals("first", tied.match("/api/x")?.name)
  }
}
