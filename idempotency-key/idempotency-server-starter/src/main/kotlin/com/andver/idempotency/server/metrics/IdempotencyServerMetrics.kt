package com.andver.idempotency.server.metrics

interface IdempotencyServerMetrics {
  fun started()
  fun completed()
  fun replayed()
  fun conflictInProgress()
  fun conflictBody()
  fun leaseTakenOver()
  fun missingKey()
  fun missingOwner()
}

object NoopIdempotencyServerMetrics : IdempotencyServerMetrics {
  override fun started() = Unit
  override fun completed() = Unit
  override fun replayed() = Unit
  override fun conflictInProgress() = Unit
  override fun conflictBody() = Unit
  override fun leaseTakenOver() = Unit
  override fun missingKey() = Unit
  override fun missingOwner() = Unit
}
