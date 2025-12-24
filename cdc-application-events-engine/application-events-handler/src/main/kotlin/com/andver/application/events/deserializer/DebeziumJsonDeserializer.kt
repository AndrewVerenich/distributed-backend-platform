package com.andver.application.events.deserializer

import com.andver.application.events.model.ChangeRecordEvent
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Deserializer
import org.slf4j.LoggerFactory
import java.util.*

class DebeziumJsonDeserializer : Deserializer<ChangeRecordEvent> {
  private val log = LoggerFactory.getLogger(this.javaClass)
  private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .setTimeZone(TimeZone.getDefault())

  override fun deserialize(topic: String, data: ByteArray): ChangeRecordEvent? {
    return try {
      objectMapper.readValue(data, ChangeRecordEvent::class.java)
    } catch (e: Exception) {
      log.error(e.message)
      null
    }
  }
}
