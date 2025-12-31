package com.andver.outbox.consumer.repository

import com.andver.outbox.publisher.model.OutboxEvent
import com.andver.outbox.publisher.model.OutboxStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface LockingOutboxRepository : ReactiveCrudRepository<OutboxEvent, Long> {
  @Query("SELECT * FROM outbox WHERE idempotency_key = CAST(:idempotencyKey AS UUID) AND type = :type FOR UPDATE")
  fun findLockingByIdempotencyKeyAndType(
    idempotencyKey: String,
    type: String,
  ): Mono<OutboxEvent>

  @Query("UPDATE outbox SET status = :newStatus WHERE idempotency_key = CAST(:idempotencyKey AS UUID) AND type = :type")
  fun updateStatusByIdempotencyKeyAndType(
    idempotencyKey: String,
    type: String,
    newStatus: OutboxStatus,
  ): Mono<Int>
}