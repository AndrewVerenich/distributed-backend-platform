package com.andver.banking.projection.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("account_balance_snapshot")
data class AccountBalanceSnapshot(
  @Id val id: Long?,
  val accountId: Long,
  val balance: BigDecimal,
  val createdAt: LocalDateTime,
)