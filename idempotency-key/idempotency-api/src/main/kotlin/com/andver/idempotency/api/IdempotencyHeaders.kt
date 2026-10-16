package com.andver.idempotency.api

object IdempotencyHeaders {
  const val KEY = "Idempotency-Key"
  const val OWNER = "Idempotency-Key-Owner"
  const val REPLAYED = "Idempotency-Replayed"
  const val OPERATION_ID = "X-Idempotency-Operation-Id"
}
