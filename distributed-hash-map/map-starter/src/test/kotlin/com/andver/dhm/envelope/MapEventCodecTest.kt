package com.andver.dhm.envelope

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MapEventCodecTest {

  data class Profile(val name: String, val tier: String)

  private val codec = MapEventCodec(
    ObjectMapper().registerModule(KotlinModule.Builder().build()).registerModule(JavaTimeModule()),
  )

  @Test
  fun `encode-decode round trip preserves all envelope fields`() {
    val original = MapEvent(
      mapName = "user-cache",
      key = "u1",
      operation = Operation.PUT,
      valueJson = """{"name":"Alice","tier":"gold"}""",
      updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
      sourceNodeId = "node-a",
    )

    val decoded = codec.decodeEvent(codec.encodeEvent(original))

    assertEquals(original, decoded)
  }

  @Test
  fun `value json round trip restores typed object`() {
    val payload = Profile("Alice", "gold")

    val json = codec.encodeValue(payload)
    val back = codec.decodeValue(json, Profile::class.java)

    assertEquals(payload, back)
  }

  @Test
  fun `tombstone envelope serializes with null value`() {
    val tomb = MapEvent(
      mapName = "user-cache",
      key = "u1",
      operation = Operation.REMOVE,
      valueJson = null,
      updatedAt = Instant.parse("2025-01-01T10:00:00Z"),
      sourceNodeId = "node-a",
    )

    val json = codec.encodeEvent(tomb)

    assert(!json.contains("valueJson")) {
      "Expected tombstone JSON to omit null valueJson, got: $json"
    }
  }
}
