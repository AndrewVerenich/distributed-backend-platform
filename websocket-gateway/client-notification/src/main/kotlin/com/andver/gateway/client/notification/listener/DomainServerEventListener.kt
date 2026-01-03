package com.andver.gateway.client.notification.listener

import com.andver.client.notification.model.server.DOMAIN_SERVER_EVENT_TOPIC
import com.andver.gateway.client.notification.model.InternalDomainEvent
import com.andver.gateway.client.notification.processor.DomainServerEventProcessor
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class DomainServerEventListener(
  private val processor: DomainServerEventProcessor,
) {

  @KafkaListener(
    topics = [DOMAIN_SERVER_EVENT_TOPIC],
    groupId = "client.notification",
    properties = [
      "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
      "value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer"
    ]
  )
  fun consume(@Payload payload: InternalDomainEvent) {
    processor.process(payload)
  }
}