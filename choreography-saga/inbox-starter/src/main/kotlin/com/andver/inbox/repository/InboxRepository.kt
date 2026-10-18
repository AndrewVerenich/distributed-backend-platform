package com.andver.inbox.repository

import com.andver.inbox.model.InboxEvent
import com.andver.inbox.model.InboxStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

interface InboxRepository : ReactiveCrudRepository<InboxEvent, String> {

  @Query(
    """
    INSERT INTO inbox (idempotency_key, event_type, status)
    VALUES (CAST(:idempotencyKey AS UUID), :eventType, 'RECEIVED')
    ON CONFLICT (idempotency_key) DO NOTHING
    """
  )
  fun insertIfAbsent(idempotencyKey: String, eventType: String): Mono<Void>

  @Query(
    """
    SELECT idempotency_key::text AS idempotency_key, event_type, status, received_at, processed_at
    FROM inbox
    WHERE idempotency_key = CAST(:idempotencyKey AS UUID)
    FOR UPDATE
    """
  )
  fun findLockingByIdempotencyKey(idempotencyKey: String): Mono<InboxEvent>

  @Query(
    """
    UPDATE inbox
    SET status = :status, processed_at = NOW()
    WHERE idempotency_key = CAST(:idempotencyKey AS UUID)
    """
  )
  fun updateStatus(idempotencyKey: String, status: InboxStatus): Mono<Int>
}
