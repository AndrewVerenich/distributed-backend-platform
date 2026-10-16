package com.andver.idempotency.server.repository

import com.andver.idempotency.api.IdempotencyStatus
import java.time.Instant

data class IdempotencyRecord(
  val owner: String,
  val key: String,
  val bodyHash: String,
  val status: IdempotencyStatus,
  val leaseUntil: Instant,
  val requestId: String,
  val responseStatus: Int? = null,
  val responseContentType: String? = null,
  val responseBody: ByteArray? = null,
  val createdAt: Instant,
  val completedAt: Instant? = null,
  val expiresAt: Instant? = null,
)
