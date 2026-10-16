package com.andver.idempotency.server.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BodyHasherTest {

  @Test
  fun `hashes raw bytes without json canonicalization`() {
    val a = BodyHasher.sha256("""{"a":1}""".toByteArray())
    val b = BodyHasher.sha256("""{"a": 1}""".toByteArray())
    assertNotEquals(a, b)
    assertEquals(64, a.length)
  }
}
