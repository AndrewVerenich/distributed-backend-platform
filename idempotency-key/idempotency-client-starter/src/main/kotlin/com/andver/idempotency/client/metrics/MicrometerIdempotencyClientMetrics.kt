package com.andver.idempotency.client.metrics

import io.micrometer.core.instrument.MeterRegistry

class MicrometerIdempotencyClientMetrics(
  private val registry: MeterRegistry,
) : IdempotencyClientMetrics {
  override fun retry(reason: String) {
    registry.counter("idempotency_client_retries", "reason", reason).increment()
  }

  override fun stopUnprocessable() {
    registry.counter("idempotency_client_stop_unprocessable").increment()
  }
}
