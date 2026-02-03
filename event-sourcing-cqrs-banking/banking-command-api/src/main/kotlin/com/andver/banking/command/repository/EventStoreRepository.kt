package com.andver.banking.command.repository

import com.andver.banking.command.model.AggregateType
import com.andver.banking.command.model.BankingEvent
import com.andver.banking.command.model.EventType
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.*

interface EventStoreRepository : ReactiveCrudRepository<BankingEvent, Long> {
  @Query(
    "SELECT COALESCE(MAX(version), 0) AS v FROM event_store WHERE aggregate_id = :aggregateId " +
        "AND aggregate_type = :aggregateType"
  )
  fun findVersionByAggregateId(aggregateType: AggregateType, aggregateId: Long): Mono<Long>

  @Query(
    """
    INSERT INTO event_store (event_id, aggregate_id, aggregate_type, event_type, payload, version, created_at)
    VALUES (:eventId, :aggregateId, :aggregateType, :eventType, CAST(:payload AS jsonb), :version, :createdAt)
  """
  )
  fun saveWithJsonb(
    eventId: UUID,
    aggregateId: Long,
    aggregateType: AggregateType,
    eventType: EventType,
    payload: String,
    version: Long,
    createdAt: LocalDateTime,
  ): Mono<Void>
}