package com.andver.idempotency.server.service

import com.andver.idempotency.server.IdempotencyContext
import com.andver.idempotency.server.repository.IdempotencyRecord
import java.time.Duration

sealed interface ClaimResult {
  data class Proceed(val context: IdempotencyContext) : ClaimResult
  data class Replay(val record: IdempotencyRecord) : ClaimResult
  data class InProgress(val retryAfter: Duration) : ClaimResult
  data object BodyConflict : ClaimResult
}
