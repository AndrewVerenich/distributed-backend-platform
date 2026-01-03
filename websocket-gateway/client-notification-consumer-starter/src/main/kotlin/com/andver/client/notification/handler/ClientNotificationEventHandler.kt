package com.andver.client.notification.handler

import com.andver.client.notification.model.client.DomainClientEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

interface ClientNotificationEventHandler {
  val eventType: String
  fun handleInternal(event: DomainClientEvent): Mono<DomainClientEvent>
}

abstract class AbstractClientNotificationEventHandler<T : Any> : ClientNotificationEventHandler {
  abstract val payloadType: Class<T>
  private val objectMapper = jacksonObjectMapper()
  protected val log = LoggerFactory.getLogger(javaClass)

  override fun handleInternal(event: DomainClientEvent): Mono<DomainClientEvent> {
    return handle(event.userId, deserializePayload(event.payload))
      .thenReturn(event)
  }

  protected abstract fun handle(userId: Long, payload: T?): Mono<Unit>

  fun deserializePayload(payloadJson: Map<String, Any?>?): T? {
    if (payloadJson == null) return null
    return objectMapper.convertValue(payloadJson, payloadType)
  }
}