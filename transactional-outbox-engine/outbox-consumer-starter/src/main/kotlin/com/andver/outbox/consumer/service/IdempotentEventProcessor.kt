package com.andver.outbox.consumer.service

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.repository.LockingOutboxRepository
import com.andver.outbox.publisher.model.OutboxStatus
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

interface IdempotentEventProcessor {
  fun process(handler: OutboxEventHandler, idempotencyKey: String): Mono<Void>
}

open class DefaultIdempotentEventProcessor(
  private val lockingOutboxRepository: LockingOutboxRepository,
) : IdempotentEventProcessor {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional
  override fun process(
    handler: OutboxEventHandler,
    idempotencyKey: String,
  ): Mono<Void> {
    return lockingOutboxRepository.findLockingByIdempotencyKeyAndType(idempotencyKey, handler.eventType)
      .switchIfEmpty(
        Mono.error(IllegalStateException("Outbox event not found: idempotencyKey=$idempotencyKey"))
      )
      .filter { outboxEvent -> outboxEvent.status != OutboxStatus.PROCESSED }
      .delayUntil { outboxEvent -> handler.handleInternal(outboxEvent) }
      .flatMap { outboxEvent ->
        log.debug("Processed outbox message with type=${handler.eventType} and idempotencyKey=$idempotencyKey")
        lockingOutboxRepository.updateStatusByIdempotencyKeyAndType(
          idempotencyKey = idempotencyKey,
          type = handler.eventType,
          newStatus = OutboxStatus.PROCESSED,
        )
      }
      .onErrorResume { exception ->
        log.error(
          "Error occurred processing outbox message with type=${handler.eventType} and idempotencyKey=$idempotencyKey",
          exception
        )
        lockingOutboxRepository.updateStatusByIdempotencyKeyAndType(
          idempotencyKey = idempotencyKey,
          type = handler.eventType,
          newStatus = OutboxStatus.FAILED,
        )
      }
      .then()
  }
}