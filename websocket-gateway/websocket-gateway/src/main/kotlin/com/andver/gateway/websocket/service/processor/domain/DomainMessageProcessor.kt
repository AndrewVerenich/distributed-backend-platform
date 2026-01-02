package com.andver.gateway.websocket.service.processor.domain

import com.andver.client.notification.model.client.DOMAIN_CLIENT_EVENT_TOPIC
import com.andver.gateway.websocket.model.ClientEventTarget
import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import com.andver.gateway.websocket.service.processor.ClientMessageProcessor
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class DomainMessageProcessor(
  private val kafkaTemplate: KafkaTemplate<String, InternalClientWebSocketEvent>
) : ClientMessageProcessor {
  override val target = ClientEventTarget.DOMAIN

  override fun process(message: InternalClientWebSocketEvent): Mono<Void> {
    return Mono.fromRunnable {
      kafkaTemplate.send(DOMAIN_CLIENT_EVENT_TOPIC, message.userId.toString(), message)
    }
  }
}
