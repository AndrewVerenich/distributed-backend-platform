package com.andver.inbox.handler

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

interface InboxEventHandler {
  val eventType: String
  fun handle(payload: String, idempotencyKey: String): Mono<Void>
}

abstract class AbstractInboxEventHandler<T : Any> : InboxEventHandler {
  abstract val payloadType: Class<T>
  private val objectMapper = jacksonObjectMapper()
  protected val log = LoggerFactory.getLogger(javaClass)

  override fun handle(payload: String, idempotencyKey: String): Mono<Void> {
    return handle(deserializePayload(payload), idempotencyKey)
  }

  protected abstract fun handle(payload: T, idempotencyKey: String): Mono<Void>

  fun deserializePayload(payloadJson: String): T {
    return objectMapper.readValue(payloadJson, payloadType)
  }
}
