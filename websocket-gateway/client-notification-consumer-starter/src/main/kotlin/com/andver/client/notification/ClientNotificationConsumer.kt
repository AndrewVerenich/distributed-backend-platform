package com.andver.client.notification

import com.andver.client.notification.handler.ClientNotificationEventHandler
import com.andver.client.notification.model.client.DomainClientEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload

class ClientNotificationConsumer(
  eventHandlers: List<ClientNotificationEventHandler>,
) {
  private val log = LoggerFactory.getLogger(ClientNotificationConsumer::class.java)
  private val handlersByType = eventHandlers.associateBy { it.eventType }

  @KafkaListener(
    topics = ["#{'\${client.notification.consumer.topics}'.split(',')}"],
    groupId = "\${client.notification.consumer.group-id}",
    properties = [
      "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
      "spring.json.value.default.type=com.andver.client.notification.model.client.DomainClientEvent",
      "spring.json.trusted.packages=*"
    ]
  )
  fun consume(
    @Payload event: DomainClientEvent,
    acknowledgment: Acknowledgment,
  ) {
    try {
      val type = event.type
      val handler = handlersByType[type]
      if (handler == null) {
        log.warn("No handler found for event type: $type")
        acknowledgment.acknowledge()
        return
      }
      handler.handleInternal(event)
        .doOnSuccess {
          log.debug("Successfully processed event={}", event)
          acknowledgment.acknowledge()
        }
        .doOnError { error ->
          log.error("Error processing event=$event, error)", error)
        }
        .subscribe()
    } catch (e: Exception) {
      log.error("Error parsing event=$event", e)
    }
  }
}