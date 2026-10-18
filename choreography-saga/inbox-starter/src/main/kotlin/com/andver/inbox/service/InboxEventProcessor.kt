package com.andver.inbox.service

import com.andver.inbox.handler.InboxEventHandler
import com.andver.inbox.model.InboxStatus
import com.andver.inbox.repository.InboxRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

interface InboxEventProcessor {
  fun process(handler: InboxEventHandler, idempotencyKey: String, payload: String): Mono<Void>
}

open class DefaultInboxEventProcessor(
  private val inboxRepository: InboxRepository,
  private val meterRegistry: MeterRegistry?,
) : InboxEventProcessor {

  private val log = LoggerFactory.getLogger(DefaultInboxEventProcessor::class.java)

  @Transactional
  override fun process(
    handler: InboxEventHandler,
    idempotencyKey: String,
    payload: String,
  ): Mono<Void> {
    return inboxRepository.insertIfAbsent(idempotencyKey, handler.eventType)
      .then(inboxRepository.findLockingByIdempotencyKey(idempotencyKey))
      .switchIfEmpty(
        Mono.error(IllegalStateException("Inbox row missing after insert: key=$idempotencyKey"))
      )
      .flatMap { record ->
        if (record.status == InboxStatus.PROCESSED) {
          log.debug(
            "Skipping already processed inbox event: type={} key={}",
            handler.eventType,
            idempotencyKey,
          )
          increment("inbox.duplicates", handler.eventType)
          return@flatMap Mono.empty()
        }

        handler.handle(payload, idempotencyKey)
          .then(
            Mono.defer {
              inboxRepository.updateStatus(idempotencyKey, InboxStatus.PROCESSED)
                .doOnSuccess {
                  log.debug("Processed inbox event: type={} key={}", handler.eventType, idempotencyKey)
                  increment("inbox.processed", handler.eventType)
                }
            }
          )
          .then()
          .onErrorResume { exception ->
            log.error(
              "Error processing inbox event: type={} key={}",
              handler.eventType,
              idempotencyKey,
              exception,
            )
            increment("inbox.failures", handler.eventType)
            Mono.defer {
              inboxRepository.updateStatus(idempotencyKey, InboxStatus.FAILED)
                .then(Mono.error(exception))
            }
          }
      }
  }

  private fun increment(name: String, eventType: String) {
    meterRegistry?.counter(name, "event_type", eventType)?.increment()
  }
}
