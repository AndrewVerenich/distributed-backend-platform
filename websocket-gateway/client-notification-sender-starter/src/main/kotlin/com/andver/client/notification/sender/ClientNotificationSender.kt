package com.andver.outbox.publisher

import com.andver.client.notification.model.server.DOMAIN_SERVER_EVENT_TOPIC
import com.andver.client.notification.model.server.DomainServerEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import reactor.core.publisher.Mono

interface ClientNotificationSender {
  fun send(event: DomainServerEvent<out Any>): Mono<Unit>
}

open class DefaultClientNotificationSender(
  private val kafkaTemplate: KafkaTemplate<String, DomainServerEvent<out Any>>,
) : ClientNotificationSender {

  private val log = LoggerFactory.getLogger(DefaultClientNotificationSender::class.java)

  override fun send(event: DomainServerEvent<out Any>): Mono<Unit> {
    return Mono.fromCallable {
      log.debug("Send client notification event={}", event)
      kafkaTemplate.send(DOMAIN_SERVER_EVENT_TOPIC, event.userId.toString(), event)
    }
      .thenReturn(Unit)
  }
}