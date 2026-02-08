package com.andver.banking.projection.repository

import com.andver.banking.projection.model.AccountBalance
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime

interface AccountBalanceRepository : ReactiveCrudRepository<AccountBalance, Long> {
  @Query(
    """
  INSERT INTO account_balance(id, owner_id, balance, updated_at)
  VALUES (:accountId, :ownerId, :balance, :updatedAt)
  """
  )
  fun create(accountId: Long, ownerId: Long, balance: BigDecimal, updatedAt: LocalDateTime): Mono<Void>
}