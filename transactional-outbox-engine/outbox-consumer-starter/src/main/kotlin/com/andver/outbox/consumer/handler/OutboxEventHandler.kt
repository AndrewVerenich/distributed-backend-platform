package com.andver.outbox.consumer.handler

import com.andver.outbox.publisher.model.OutboxEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

interface OutboxEventHandler {
  val eventType: String
  fun handleInternal(event: OutboxEvent): Mono<OutboxEvent>
}

abstract class AbstractOutboxEventHandler<T : Any> : OutboxEventHandler {
  abstract val payloadType: Class<T>
  private val objectMapper = jacksonObjectMapper()
  protected val log = LoggerFactory.getLogger(javaClass)

  override fun handleInternal(event: OutboxEvent): Mono<OutboxEvent> {
    return handle(event, deserializePayload(event.payload))
      .thenReturn(event)
  }

  protected abstract fun handle(event: OutboxEvent, payload: T): Mono<Void>

  fun deserializePayload(payloadJson: String): T {
    return objectMapper.readValue(payloadJson, payloadType)
  }
}