package com.andver.banking.query.repository

import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.entity.BankingEvent
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import java.time.LocalDateTime

interface EventStoreRepository : ReactiveCrudRepository<BankingEvent, Long> {
  fun findAllBankingEventByAggregateIdAndAggregateTypeAndCreatedAtBeforeOrderByVersion(
    aggregateId: Long,
    aggregateType: AggregateType,
    time: LocalDateTime,
  ): Flux<BankingEvent>

  fun findAllBankingEventByAggregateIdAndAggregateTypeAndCreatedAtAfterAndCreatedAtBeforeOrderByVersion(
    aggregateId: Long,
    aggregateType: AggregateType,
    startTime: LocalDateTime,
    endTime: LocalDateTime,
  ): Flux<BankingEvent>
}