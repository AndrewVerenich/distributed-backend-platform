package com.andver.outbox.publisher.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("outbox")
data class OutboxEvent(
  @Id
  val id: Long? = null,
  @Column("partitioning_key")
  val partitioningKey: String,
  val type: String,
  val payload: String,
  @Column("idempotency_key")
  val idempotencyKey: String? = null,
  val status: OutboxStatus = OutboxStatus.PENDING,
  @Column("created_at")
  val createdAt: LocalDateTime? = null,
  @Column("processed_at")
  val processedAt: LocalDateTime? = null
)

enum class OutboxStatus {
  PENDING,
  PROCESSED,
  FAILED,
}