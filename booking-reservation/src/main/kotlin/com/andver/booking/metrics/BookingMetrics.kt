package com.andver.booking.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class BookingMetrics(
  registry: MeterRegistry,
) {
  private val holdsOk = registry.counter("booking_holds_ok")
  private val holdConflicts = registry.counter("booking_hold_conflicts")
  private val confirmsOk = registry.counter("booking_confirms_ok")
  private val confirmConflicts = registry.counter("booking_confirm_conflicts")
  private val expiredReleases = registry.counter("booking_expired_releases")
  private val compensations = registry.counter("booking_compensations")
  private val oversell = registry.counter("booking_oversell")
  private val naiveHolds = registry.counter("booking_naive_holds_ok")
  private val idempotencyReplay = registry.counter("booking_idempotency_replayed")
  private val idempotencyConflict = registry.counter("booking_idempotency_conflict")

  fun holdsOk() = holdsOk.increment()
  fun holdConflicts() = holdConflicts.increment()
  fun confirmsOk() = confirmsOk.increment()
  fun confirmConflicts() = confirmConflicts.increment()
  fun expiredReleases(n: Long = 1) = expiredReleases.increment(n.toDouble())
  fun compensations() = compensations.increment()
  fun oversell() = oversell.increment()
  fun naiveHoldsOk() = naiveHolds.increment()
  fun idempotencyReplayed() = idempotencyReplay.increment()
  fun idempotencyConflict() = idempotencyConflict.increment()
}
