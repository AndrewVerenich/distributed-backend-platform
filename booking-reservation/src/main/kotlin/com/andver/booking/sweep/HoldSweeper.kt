package com.andver.booking.sweep

import com.andver.booking.metrics.BookingMetrics
import com.andver.booking.repository.CompensationOutboxRepository
import com.andver.booking.repository.ReservationRepository
import com.andver.booking.repository.SeatRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

@Component
class HoldSweeper(
  private val seats: SeatRepository,
  private val reservations: ReservationRepository,
  private val outbox: CompensationOutboxRepository,
  private val metrics: BookingMetrics,
  private val tx: TransactionalOperator,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val sweeping = AtomicBoolean(false)
  private val publishing = AtomicBoolean(false)

  @Scheduled(fixedDelayString = "\${booking.sweeper-interval-ms:1000}")
  fun sweepExpired() {
    if (!sweeping.compareAndSet(false, true)) return
    tx.transactional(
      seats.releaseExpiredHolds()
        .collectList()
        .flatMap { released ->
          if (released.isEmpty()) return@flatMap Mono.empty<Void>()
          val reservationIds = released.mapNotNull { it.reservationId }
          metrics.expiredReleases(released.size.toLong())
          log.debug("Released {} expired holds", released.size)
          reservations.markExpired(reservationIds).then()
        },
    )
      .doFinally { sweeping.set(false) }
      .subscribe(
        {},
        { ex -> log.warn("Sweeper failed: {}", ex.message) },
      )
  }

  @Scheduled(fixedDelayString = "\${booking.outbox-interval-ms:2000}")
  fun publishCompensations() {
    if (!publishing.compareAndSet(false, true)) return
    outbox.claimUnpublished()
      .doOnNext { row ->
        log.info("Compensation published reservationId={} payload={}", row.reservationId, row.payload)
      }
      .then()
      .doFinally { publishing.set(false) }
      .subscribe(
        {},
        { ex -> log.warn("Outbox publish failed: {}", ex.message) },
      )
  }
}
