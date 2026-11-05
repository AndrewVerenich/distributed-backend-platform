package com.andver.clientratelimit.headers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.time.Duration

class RateLimitHeaderParserTest {

  @Test
  fun `prefers RateLimit-Reset delta over X-RateLimit-Reset and Retry-After`() {
    val headers = HttpHeaders().apply {
      add(RateLimitHeaderNames.DRAFT_RESET, "7")
      add(RateLimitHeaderNames.X_RESET, "1893456000")
      add(RateLimitHeaderNames.RETRY_AFTER, "3")
    }
    val wait = RateLimitHeaderParser.parse(headers).resolveWait(nowEpochSeconds = 1_000)
    assertEquals(Duration.ofSeconds(7), wait)
  }

  @Test
  fun `uses X-RateLimit-Reset epoch when draft reset missing`() {
    val headers = HttpHeaders().apply {
      add(RateLimitHeaderNames.X_RESET, "1100")
      add(RateLimitHeaderNames.RETRY_AFTER, "99")
    }
    val wait = RateLimitHeaderParser.parse(headers).resolveWait(nowEpochSeconds = 1_000)
    assertEquals(Duration.ofSeconds(100), wait)
  }

  @Test
  fun `falls back to Retry-After`() {
    val headers = HttpHeaders().apply {
      add(RateLimitHeaderNames.RETRY_AFTER, "4")
    }
    val wait = RateLimitHeaderParser.parse(headers).resolveWait()
    assertEquals(Duration.ofSeconds(4), wait)
  }

  @Test
  fun `returns null when no usable headers`() {
    assertNull(RateLimitHeaderParser.parse(HttpHeaders()).resolveWait())
  }

  @Test
  fun `clamps past X-RateLimit-Reset to zero`() {
    val headers = HttpHeaders().apply {
      add(RateLimitHeaderNames.X_RESET, "500")
    }
    val wait = RateLimitHeaderParser.parse(headers).resolveWait(nowEpochSeconds = 1_000)
    assertEquals(Duration.ZERO, wait)
  }
}
