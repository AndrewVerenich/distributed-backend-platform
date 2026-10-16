package com.andver.idempotency.server.metrics

import io.micrometer.core.instrument.MeterRegistry

class MicrometerIdempotencyServerMetrics(
  registry: MeterRegistry,
) : IdempotencyServerMetrics {

  private val started = registry.counter("idempotency_server_started")
  private val completed = registry.counter("idempotency_server_completed")
  private val replayed = registry.counter("idempotency_server_replayed")
  private val conflictInProgress = registry.counter("idempotency_server_conflict_in_progress")
  private val conflictBody = registry.counter("idempotency_server_conflict_body")
  private val leaseTakenOver = registry.counter("idempotency_server_lease_taken_over")
  private val missingKey = registry.counter("idempotency_server_missing_key")
  private val missingOwner = registry.counter("idempotency_server_missing_owner")

  override fun started() = started.increment()
  override fun completed() = completed.increment()
  override fun replayed() = replayed.increment()
  override fun conflictInProgress() = conflictInProgress.increment()
  override fun conflictBody() = conflictBody.increment()
  override fun leaseTakenOver() = leaseTakenOver.increment()
  override fun missingKey() = missingKey.increment()
  override fun missingOwner() = missingOwner.increment()
}
