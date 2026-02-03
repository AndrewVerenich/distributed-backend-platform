package com.andver.banking.command.model

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

enum class EventType {
  ACCOUNT_OPENED,
  MONEY_DEPOSITED,
  MONEY_WITHDRAWN;

  companion object {
    fun from(command: AccountCommand): EventType = when (command) {
      is AccountCommand.OpenAccount -> ACCOUNT_OPENED
      is AccountCommand.DepositMoney -> MONEY_DEPOSITED
      is AccountCommand.WithdrawMoney -> MONEY_WITHDRAWN
    }
  }
}

enum class AggregateType {
  ACCOUNT;

  companion object {
    fun from(command: AccountCommand): AggregateType = when (command) {
      is AccountCommand -> ACCOUNT
    }
  }
}

