package com.andver.idempotency.server

data class IdempotencyContext(
  val owner: String,
  val key: String,
  val requestId: String,
  val bodyHash: String,
)
