package com.andver.waitingroom.queue

import com.andver.waitingroom.metrics.WaitingRoomMetrics
import com.andver.waitingroom.ticket.TicketService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

data class AdmittedVisitor(
  val eventId: String,
  val visitorId: String,
  val ticketId: String,
  val wait: Duration?,
)

/**
 * Pops FIFO head, issues opaque tickets, marks visitors ADMITTED.
 * Used by AdmitWorker under a per-event Redis lock.
 */
class AdmissionService(
  private val queue: FifoQueueService,
  private val tickets: TicketService,
  private val metrics: WaitingRoomMetrics,
) {

  fun admitBatch(eventId: String, limit: Int): Flux<AdmittedVisitor> {
    if (limit < 1) return Flux.empty()
    val now = Instant.now().toEpochMilli()
    return queue.popAdmitted(eventId, limit)
      .concatMap { visitorId -> admitOne(eventId, visitorId, now) }
      .collectList()
      .flatMapMany { admitted ->
        if (admitted.isNotEmpty()) {
          metrics.admit(eventId, admitted.size)
        }
        queue.queueDepth(eventId)
          .doOnNext { metrics.setQueueDepth(eventId, it) }
          .thenMany(Flux.fromIterable(admitted))
      }
  }

  fun tryAdmitUnderLock(eventId: String, limit: Int): Flux<AdmittedVisitor> =
    Flux.usingWhen(
      queue.tryAcquireAdmitLock(eventId).filter { it }.thenReturn(eventId),
      { id -> admitBatch(id, limit) },
      { id -> queue.releaseAdmitLock(id) },
    )

  private fun admitOne(eventId: String, visitorId: String, nowEpochMs: Long): Mono<AdmittedVisitor> =
    tickets.issue(eventId, visitorId)
      .flatMap { ticketId ->
        queue.markAdmitted(eventId, visitorId, ticketId)
          .map { joinedAt ->
            val wait = if (joinedAt > 0) {
              Duration.ofMillis((nowEpochMs - joinedAt).coerceAtLeast(0)).also {
                metrics.recordWait(eventId, it)
              }
            } else {
              null
            }
            AdmittedVisitor(eventId, visitorId, ticketId, wait)
          }
      }
}
