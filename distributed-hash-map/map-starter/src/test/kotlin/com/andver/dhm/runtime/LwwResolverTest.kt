package com.andver.dhm.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LwwResolverTest {

  private val t0 = Instant.parse("2025-01-01T00:00:00Z")
  private val t1 = Instant.parse("2025-01-01T00:00:01Z")

  @Test
  fun `incoming wins when no existing entry`() {
    val incoming = LocalEntry.Value("v", t0, "node-a")

    assertTrue(LwwResolver.shouldApply(existing = null, incoming = incoming))
  }

  @Test
  fun `strictly newer incoming wins`() {
    val existing = LocalEntry.Value("old", t0, "node-a")
    val incoming = LocalEntry.Value("new", t1, "node-b")

    assertTrue(LwwResolver.shouldApply(existing, incoming))
  }

  @Test
  fun `strictly older incoming loses`() {
    val existing = LocalEntry.Value("new", t1, "node-a")
    val incoming = LocalEntry.Value("old", t0, "node-b")

    assertFalse(LwwResolver.shouldApply(existing, incoming))
  }

  @Test
  fun `same timestamp resolves by lexicographically larger source id`() {
    val existing = LocalEntry.Value("a", t0, "node-a")
    val incoming = LocalEntry.Value("b", t0, "node-b")

    assertTrue(LwwResolver.shouldApply(existing, incoming))
    assertFalse(LwwResolver.shouldApply(incoming, existing))
  }

  @Test
  fun `tombstone vs value is decided purely by timestamp and source id`() {
    val value = LocalEntry.Value("payload", t0, "node-a")
    val newerTombstone = LocalEntry.Tombstone(t1, "node-a")

    assertTrue(LwwResolver.shouldApply(value, newerTombstone))
    assertFalse(LwwResolver.shouldApply(newerTombstone, value))
  }
}
