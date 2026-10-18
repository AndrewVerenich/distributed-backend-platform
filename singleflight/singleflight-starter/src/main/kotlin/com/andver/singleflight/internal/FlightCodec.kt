package com.andver.singleflight.internal

import com.fasterxml.jackson.databind.ObjectMapper

class FlightCodec(
  private val objectMapper: ObjectMapper,
) {
  fun <T : Any> encodeSuccess(value: T): FlightEnvelope =
    FlightEnvelope.success(objectMapper.writeValueAsString(value))

  fun encodeError(error: Throwable): FlightEnvelope =
    FlightEnvelope.error(
      message = error.message ?: error.javaClass.simpleName,
      errorClass = error.javaClass.name,
    )

  fun <T : Any> decode(envelope: FlightEnvelope, type: Class<T>): T {
    if (!envelope.ok || envelope.payloadJson == null) {
      throw IllegalStateException("Cannot decode non-success envelope")
    }
    return objectMapper.readValue(envelope.payloadJson, type)
  }

  fun write(envelope: FlightEnvelope): String = objectMapper.writeValueAsString(envelope)

  fun read(json: String): FlightEnvelope = objectMapper.readValue(json, FlightEnvelope::class.java)
}
