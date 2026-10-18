package com.andver.inbox

import com.andver.inbox.handler.InboxEventHandler
import com.andver.inbox.service.InboxEventProcessor
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload

class InboxEventConsumer(
  eventHandlers: List<InboxEventHandler>,
  private val processor: InboxEventProcessor,
) {
  private val log = LoggerFactory.getLogger(InboxEventConsumer::class.java)
  private val handlersByType = eventHandlers.associateBy { it.eventType }

  @KafkaListener(
    topics = ["#{'\${inbox.consumer.topics}'.split(',')}"],
    groupId = "\${inbox.consumer.group-id}",
    properties = [
      "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
    ],
  )
  fun consume(
    @Payload payload: String,
    @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
    @Header(value = "idempotencyKey", required = true) idempotencyKey: String,
    acknowledgment: Acknowledgment,
  ) {
    val type = extractEventTypeFromTopic(topic)
    val handler = handlersByType[type]
    if (handler == null) {
      log.warn("No inbox handler for event type={} topic={}", type, topic)
      acknowledgment.acknowledge()
      return
    }

    try {
      processor.process(handler, normalizeIdempotencyKey(idempotencyKey), normalizePayload(payload))
        .block()
      acknowledgment.acknowledge()
    } catch (e: Exception) {
      log.error("Failed to process inbox event type={} key={}", type, idempotencyKey, e)
      // Do not ack — Kafka will redeliver for at-least-once + inbox dedupe.
    }
  }

  private fun extractEventTypeFromTopic(topic: String): String = topic.removePrefix("domain.")

  /**
   * Debezium may wrap UUID header as JSON string with quotes.
   */
  private fun normalizeIdempotencyKey(raw: String): String =
    raw.trim().removePrefix("\"").removeSuffix("\"")

  /**
   * EventRouter + StringConverter sometimes wraps JSON payload in extra quotes.
   */
  private fun normalizePayload(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length - 1)
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
    }
    return trimmed
  }
}
