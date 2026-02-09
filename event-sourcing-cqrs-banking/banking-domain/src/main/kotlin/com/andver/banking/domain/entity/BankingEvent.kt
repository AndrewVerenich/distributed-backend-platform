package com.andver.banking.domain.entity

import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.*

@Table("event_store")
data class BankingEvent(
  @Id val id: Long? = null,
  val eventId: UUID,
  val aggregateId: Long,
  val aggregateType: AggregateType = AggregateType.ACCOUNT,
  val eventType: EventType,
  val payload: String,
  val version: Long,
  val createdAt: LocalDateTime,
)

