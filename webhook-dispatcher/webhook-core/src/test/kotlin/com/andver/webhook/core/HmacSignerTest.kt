package com.andver.webhook.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HmacSignerTest {
  @Test
  fun `sign and verify`() {
    val sig = HmacSigner.sign("secret-ok", "1710000000", "{\"a\":1}")
    assertTrue(sig.startsWith("sha256="))
    assertTrue(HmacSigner.matches("secret-ok", "1710000000", "{\"a\":1}", sig))
    assertFalse(HmacSigner.matches("wrong", "1710000000", "{\"a\":1}", sig))
  }
}
