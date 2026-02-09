package com.andver.banking.projection.repository

import com.andver.banking.domain.entity.AccountBalanceSnapshot
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime

interface AccountBalanceSnapshotRepository : ReactiveCrudRepository<AccountBalanceSnapshot, Long> {
  @Query(
    """
  INSERT INTO account_balance_snapshot(account_id, balance, created_at)
  VALUES (:accountId, :balance, :createdAt)
  """
  )
  fun create(accountId: Long, balance: BigDecimal, createdAt: LocalDateTime): Mono<Void>
}