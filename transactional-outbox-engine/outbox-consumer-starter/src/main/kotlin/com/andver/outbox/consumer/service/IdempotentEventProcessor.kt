package com.andver.outbox.consumer.service

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.metrics.OutboxConsumerMetrics
import com.andver.outbox.consumer.repository.LockingOutboxRepository
import com.andver.outbox.publisher.model.OutboxStatus
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.Duration

interface IdempotentEventProcessor {
  fun process(handler: OutboxEventHandler, idempotencyKey: String): Mono<Void>
}

open class DefaultIdempotentEventProcessor(
  private val lockingOutboxRepository: LockingOutboxRepository,
  private val metrics: OutboxConsumerMetrics = OutboxConsumerMetrics.NOOP,
) : IdempotentEventProcessor {

  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional
  override fun process(
    handler: OutboxEventHandler,
    idempotencyKey: String,
  ): Mono<Void> {
    val started = System.nanoTime()
    return lockingOutboxRepository.findLockingByIdempotencyKeyAndType(idempotencyKey, handler.eventType)
      .switchIfEmpty(
        Mono.error(IllegalStateException("Outbox event not found: idempotencyKey=$idempotencyKey"))
      )
      .flatMap { outboxEvent ->
        if (outboxEvent.status == OutboxStatus.PROCESSED) {
          metrics.recordProcessed(handler.eventType, "skipped")
          metrics.recordProcessDuration(handler.eventType, Duration.ofNanos(System.nanoTime() - started))
          Mono.empty()
        } else {
          handler.handleInternal(outboxEvent)
            .flatMap {
              log.debug("Processed outbox message with type=${handler.eventType} and idempotencyKey=$idempotencyKey")
              lockingOutboxRepository.updateStatusByIdempotencyKeyAndType(
                idempotencyKey = idempotencyKey,
                type = handler.eventType,
                newStatus = OutboxStatus.PROCESSED,
              ).doOnSuccess {
                metrics.recordProcessed(handler.eventType, "processed")
                metrics.recordProcessDuration(handler.eventType, Duration.ofNanos(System.nanoTime() - started))
              }
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
              ).doOnSuccess {
                metrics.recordProcessed(handler.eventType, "failed")
                metrics.recordProcessDuration(handler.eventType, Duration.ofNanos(System.nanoTime() - started))
              }
            }
        }
      }
      .onErrorResume { exception ->
        // not found / unexpected before handle
        if (exception is IllegalStateException) {
          metrics.recordProcessed(handler.eventType, "failed")
          metrics.recordProcessDuration(handler.eventType, Duration.ofNanos(System.nanoTime() - started))
          lockingOutboxRepository.updateStatusByIdempotencyKeyAndType(
            idempotencyKey = idempotencyKey,
            type = handler.eventType,
            newStatus = OutboxStatus.FAILED,
          )
        } else {
          Mono.error(exception)
        }
      }
      .then()
  }
}
