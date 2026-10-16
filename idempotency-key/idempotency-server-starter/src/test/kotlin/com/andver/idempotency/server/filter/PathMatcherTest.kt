package com.andver.idempotency.server.filter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PathMatcherTest {

  private val matcher = PathMatcher(listOf("/charges", "/api/v1/pay"))

  @Test
  fun `matches exact and nested paths`() {
    assertTrue(matcher.matches("/charges"))
    assertTrue(matcher.matches("/charges/extra"))
    assertTrue(matcher.matches("/api/v1/pay"))
    assertFalse(matcher.matches("/actuator/health"))
    assertFalse(matcher.matches("/charge"))
  }
}
