package com.andver.webhook.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SsrfGuardTest {
  @Test
  fun `blocks metadata host`() {
    val result = SsrfGuard.validate("http://metadata.google.internal/latest", allowPrivate = true)
    assertTrue(result.isFailure)
  }

  @Test
  fun `allows private when enabled`() {
    val result = SsrfGuard.validate("http://10.0.0.5:8243/hooks/ok", allowPrivate = true)
    assertTrue(result.isSuccess)
  }

  @Test
  fun `blocks private when disabled`() {
    val result = SsrfGuard.validate("http://10.0.0.5:8243/hooks/ok", allowPrivate = false)
    assertTrue(result.isFailure)
  }
}
