package com.andver.banking.projection.model

import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.time.LocalDateTime
import java.util.*

data class BankingEvent(
  val id: Long,
  @JsonProperty("event_id")
  val eventId: String,
  @JsonProperty("aggregate_id")
  val aggregateId: Long,
  @JsonProperty("aggregate_type")
  val aggregateType: AggregateType,
  @JsonProperty("event_type")
  val eventType: EventType,
  val payload: String,
  val version: Long,
  @JsonProperty("created_at")
  val createdAt: Long,
)



