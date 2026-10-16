package com.andver.idempotency.client.metrics

interface IdempotencyClientMetrics {
  fun retry(reason: String)
  fun stopUnprocessable()
}

object NoopIdempotencyClientMetrics : IdempotencyClientMetrics {
  override fun retry(reason: String) = Unit
  override fun stopUnprocessable() = Unit
}
