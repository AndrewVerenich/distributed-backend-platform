package com.andver.dhm.envelope

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Thin Jackson-based codec for [MapEvent] envelopes and per-map value payloads.
 *
 * Kept as an explicit collaborator (rather than scattered `objectMapper.writeValueAsString` calls)
 * so that the envelope wire format is owned in a single place and easy to evolve.
 */
class MapEventCodec(private val objectMapper: ObjectMapper) {

  fun encodeEvent(event: MapEvent): String = objectMapper.writeValueAsString(event)

  fun decodeEvent(payload: String): MapEvent = objectMapper.readValue(payload, MapEvent::class.java)

  fun <V : Any> encodeValue(value: V): String = objectMapper.writeValueAsString(value)

  fun <V : Any> decodeValue(json: String, valueType: Class<V>): V =
    objectMapper.readValue(json, valueType)
}
