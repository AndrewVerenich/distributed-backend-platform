package com.andver.outbox.consumer

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.service.IdempotentEventProcessor
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header

class OutboxEventConsumer(
  eventHandlers: List<OutboxEventHandler>,
  private val processor: IdempotentEventProcessor,
) {
  private val log = LoggerFactory.getLogger(OutboxEventConsumer::class.java)
  private val handlersByType = eventHandlers.associateBy { it.eventType }

  @KafkaListener(
    topics = ["\${outbox.consumer.topics}"],
    groupId = "\${outbox.consumer.group-id:\${spring.application.name}-outbox}",
    properties = [
      "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
    ]
  )
  fun consume(
    @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
    @Header(value = "idempotencyKey", required = true) idempotencyKey: String,
    acknowledgment: Acknowledgment
  ) {
    try {
      val type = extractEventTypeFromTopic(topic)
      val handler = handlersByType[type]
      if (handler == null) {
        log.warn("No handler found for event type: $type")
        acknowledgment.acknowledge()
        return
      }
      processor.process(handler, idempotencyKey)
        .doOnSuccess {
          log.debug("Successfully processed event: type=$type, idempotencyKey=${idempotencyKey}")
          acknowledgment.acknowledge()
        }
        .doOnError { error ->
          log.error("Error processing event: type=$type, idempotencyKey=${idempotencyKey}", error)
        }
        .subscribe()
    } catch (e: Exception) {
      log.error("Error parsing event from topic: $topic", e)
    }
  }

  private fun extractEventTypeFromTopic(topic: String): String {
    return topic.removePrefix("domain.")
  }
}