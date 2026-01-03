package com.andver.gateway.client.notification.listener

import com.andver.client.notification.model.client.DOMAIN_CLIENT_EVENT_TOPIC
import com.andver.gateway.client.notification.model.InternalDomainEvent
import com.andver.gateway.client.notification.processor.DomainClientEventProcessor
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class DomainClientEventListener(
  private val processor: DomainClientEventProcessor,
) {

  @KafkaListener(
    topics = [DOMAIN_CLIENT_EVENT_TOPIC],
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