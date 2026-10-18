package com.andver.inbox.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("inbox")
data class InboxEvent(
  @Id
  @Column("idempotency_key")
  val idempotencyKey: String,
  @Column("event_type")
  val eventType: String,
  val status: InboxStatus = InboxStatus.RECEIVED,
  @Column("received_at")
  val receivedAt: LocalDateTime? = null,
  @Column("processed_at")
  val processedAt: LocalDateTime? = null,
)

enum class InboxStatus {
  RECEIVED,
  PROCESSED,
  FAILED,
}
