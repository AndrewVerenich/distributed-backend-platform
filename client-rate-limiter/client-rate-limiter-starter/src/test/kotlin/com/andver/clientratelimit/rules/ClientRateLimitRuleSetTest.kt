package com.andver.clientratelimit.rules

import com.andver.clientratelimit.ClientRateLimiterProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class ClientRateLimitRuleSetTest {

  private val rules = ClientRateLimitRuleSet.from(
    ClientRateLimiterProperties(
      rules = listOf(
        ClientRateLimiterProperties.RuleProperties(
          name = "catalog",
          pathPrefixes = listOf("/api/catalog"),
          limit = 20,
          window = Duration.ofSeconds(1),
          burst = 0,
        ),
        ClientRateLimiterProperties.RuleProperties(
          name = "checkout",
          pathPrefixes = listOf("/api/checkout"),
          limit = 5,
          window = Duration.ofSeconds(1),
          burst = 5,
        ),
        ClientRateLimiterProperties.RuleProperties(
          name = "api-root",
          pathPrefixes = listOf("/api"),
          limit = 100,
          window = Duration.ofSeconds(1),
        ),
      ),
    ),
  )

  @Test
  fun `longest prefix wins`() {
    assertEquals("catalog", rules.match("/api/catalog/items")?.name)
    assertEquals("checkout", rules.match("/api/checkout")?.name)
  }

  @Test
  fun `falls back to shorter prefix`() {
    assertEquals("api-root", rules.match("/api/other")?.name)
  }

  @Test
  fun `no match returns null`() {
    assertNull(rules.match("/health"))
  }

  @Test
  fun `burst defaults to limit when zero`() {
    assertEquals(20, rules.match("/api/catalog")?.burst)
  }
}
