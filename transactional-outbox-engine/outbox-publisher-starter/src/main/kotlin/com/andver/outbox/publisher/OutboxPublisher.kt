package com.andver.outbox.publisher

import com.andver.outbox.publisher.model.OutboxEvent
import com.andver.outbox.publisher.repository.WriteOutboxRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

interface OutboxPublisher {
  fun publish(
    partitioningKey: String,
    eventType: String,
    payload: Any,
  ): Mono<OutboxEvent>
}

open class DefaultOutboxPublisher(
  private val repository: WriteOutboxRepository,
) : OutboxPublisher {

  private val objectMapper = jacksonObjectMapper()
  private val log = LoggerFactory.getLogger(DefaultOutboxPublisher::class.java)

  @Transactional(propagation = Propagation.MANDATORY)
  override fun publish(
    partitioningKey: String,
    eventType: String,
    payload: Any
  ): Mono<OutboxEvent> {
    log.debug("Publishing outbox event: type=$eventType, partitioningKey=$partitioningKey, payload=$payload")
    return OutboxEvent(
      partitioningKey = partitioningKey,
      type = eventType,
      payload = objectMapper.writeValueAsString(payload),
    ).let { event -> repository.save(event) }
  }
}