package com.andver.banking.query.repository

import com.andver.banking.query.model.AccountBalanceSnapshot
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.time.LocalDateTime

interface AccountBalanceSnapshotRepository : ReactiveCrudRepository<AccountBalanceSnapshot, Long> {
  fun findFirstByAccountIdAndCreatedAtIsBeforeOrderByCreatedAtDesc(
    accountId: Long,
    date: LocalDateTime
  ): Mono<AccountBalanceSnapshot>
}