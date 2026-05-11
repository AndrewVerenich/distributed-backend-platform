package com.andver.dhm.runtime

import com.andver.dhm.envelope.MapEvent
import com.andver.dhm.envelope.MapEventCodec
import com.andver.dhm.envelope.Operation
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LocalStateTest {

  data class Profile(val name: String, val tier: String = "standard")

  private val codec = MapEventCodec(
    ObjectMapper().registerModule(KotlinModule.Builder().build()).registerModule(JavaTimeModule()),
  )

  private val t0 = Instant.parse("2025-01-01T00:00:00Z")
  private val t1 = Instant.parse("2025-01-01T00:00:01Z")
  private val t2 = Instant.parse("2025-01-01T00:00:02Z")

  private fun newState() = LocalState("user-cache", Profile::class.java, codec)

  private fun put(key: String, profile: Profile, ts: Instant, source: String) =
    MapEvent(
      mapName = "user-cache",
      key = key,
      operation = Operation.PUT,
      valueJson = codec.encodeValue(profile),
      updatedAt = ts,
      sourceNodeId = source,
    )

  private fun remove(key: String, ts: Instant, source: String) =
    MapEvent(
      mapName = "user-cache",
      key = key,
      operation = Operation.REMOVE,
      valueJson = null,
      updatedAt = ts,
      sourceNodeId = source,
    )

  @Test
  fun `applies first PUT and exposes value`() {
    val state = newState()

    val changed = state.apply(put("u1", Profile("Alice"), t0, "node-a"))

    assertTrue(changed)
    assertEquals(Profile("Alice"), state.get("u1"))
    assertTrue(state.containsLive("u1"))
    assertEquals(1, state.liveSize())
    assertEquals(0, state.tombstoneSize())
  }

  @Test
  fun `LWW rejects older PUT after newer PUT`() {
    val state = newState()
    state.apply(put("u1", Profile("Alice"), t1, "node-a"))

    val changed = state.apply(put("u1", Profile("ALICE-stale"), t0, "node-b"))

    assertFalse(changed)
    assertEquals(Profile("Alice"), state.get("u1"))
    assertEquals(1, state.appliedCount())
    assertEquals(1, state.rejectedCount())
  }

  @Test
  fun `REMOVE creates tombstone and hides value`() {
    val state = newState()
    state.apply(put("u1", Profile("Alice"), t0, "node-a"))

    val changed = state.apply(remove("u1", t1, "node-a"))

    assertTrue(changed)
    assertNull(state.get("u1"))
    assertFalse(state.containsLive("u1"))
    assertEquals(0, state.liveSize())
    assertEquals(1, state.tombstoneSize())
  }

  @Test
  fun `late PUT after REMOVE is ignored when older than tombstone`() {
    val state = newState()
    state.apply(put("u1", Profile("Alice"), t0, "node-a"))
    state.apply(remove("u1", t2, "node-a"))

    val changed = state.apply(put("u1", Profile("Late"), t1, "node-b"))

    assertFalse(changed)
    assertNull(state.get("u1"))
    assertEquals(1, state.tombstoneSize())
  }

  @Test
  fun `newer PUT after REMOVE resurrects the key`() {
    val state = newState()
    state.apply(remove("u1", t0, "node-a"))

    val changed = state.apply(put("u1", Profile("Born"), t1, "node-b"))

    assertTrue(changed)
    assertEquals(Profile("Born"), state.get("u1"))
    assertEquals(1, state.liveSize())
    assertEquals(0, state.tombstoneSize())
  }

  @Test
  fun `tombstones older than retention are evicted`() {
    val state = newState()
    state.apply(remove("u1", t0, "node-a"))
    state.apply(remove("u2", t1, "node-a"))

    val now = t2.plusSeconds(60)
    val evicted = state.evictTombstonesOlderThan(now, Duration.ofSeconds(30))

    assertEquals(2, evicted)
    assertEquals(0, state.tombstoneSize())
  }

  @Test
  fun `snapshot returns only live entries`() {
    val state = newState()
    state.apply(put("u1", Profile("Alice"), t0, "node-a"))
    state.apply(put("u2", Profile("Bob"), t0, "node-a"))
    state.apply(remove("u2", t1, "node-a"))

    val snap = state.snapshot()

    assertEquals(1, snap.size)
    assertEquals(Profile("Alice"), snap["u1"])
  }
}
