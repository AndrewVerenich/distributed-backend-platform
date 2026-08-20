package com.andver.push.sender

import com.andver.push.model.PUSH_SERVER_EVENT_TOPIC
import com.andver.push.model.PushEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import reactor.core.publisher.Mono

interface PushEventSender {
  fun send(event: PushEvent): Mono<Unit>
}

open class DefaultPushEventSender(
  private val kafkaTemplate: KafkaTemplate<String, PushEvent>,
) : PushEventSender {

  private val log = LoggerFactory.getLogger(DefaultPushEventSender::class.java)

  override fun send(event: PushEvent): Mono<Unit> {
    return Mono.fromCallable {
      log.debug("Send push event clientId={} type={}", event.clientId, event.type)
      kafkaTemplate.send(PUSH_SERVER_EVENT_TOPIC, event.clientId, event)
    }.thenReturn(Unit)
  }
}
