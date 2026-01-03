package com.andver.gateway.client.notification.processor

import com.andver.client.notification.model.client.DomainClientEventType
import com.andver.gateway.client.notification.model.InternalDomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

interface DomainClientEventProcessor {
  fun process(event: InternalDomainEvent)
}

@Component
class DefaultDomainClientEventProcessor(
  private val kafkaTemplate: KafkaTemplate<String, InternalDomainEvent>,
  private val objectMapper: ObjectMapper
) : DomainClientEventProcessor {
  private val logger = LogManager.getLogger(DefaultDomainClientEventProcessor::class.java)
  override fun process(event: InternalDomainEvent) {
    logger.info("---> Receive websocket event from client event=$event")
    if (isValidPayload(event)) {
      kafkaTemplate.send(event.type, event.userId.toString(), event)
    } else {
      logger.warn("---> Cannot deserialize event =$event")
    }
  }

  private fun isValidPayload(event: InternalDomainEvent): Boolean {
    val payloadClazz = DomainClientEventType.fromTopic(event.type)?.payloadClazz ?: return true
    return try {
      objectMapper.convertValue(event.payload, payloadClazz) != null
    } catch (_: Exception) {
      false
    }
  }
}
