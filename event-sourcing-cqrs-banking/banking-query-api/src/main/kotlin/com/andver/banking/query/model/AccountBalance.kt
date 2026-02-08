package com.andver.banking.query.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Table("account_balance")
data class AccountBalance(
  @Id val id: Long?,
  val ownerId: Long,
  val balance: BigDecimal,
  val updatedAt: LocalDateTime,
)