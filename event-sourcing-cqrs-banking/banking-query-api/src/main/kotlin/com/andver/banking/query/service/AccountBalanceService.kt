package com.andver.banking.query.service

import com.andver.banking.domain.AccountCommand
import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import com.andver.banking.domain.entity.AccountBalance
import com.andver.banking.domain.entity.AccountBalanceSnapshot
import com.andver.banking.domain.entity.BankingEvent
import com.andver.banking.query.metrics.QueryMetrics
import com.andver.banking.query.repository.AccountBalanceRepository
import com.andver.banking.query.repository.AccountBalanceSnapshotRepository
import com.andver.banking.query.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import reactor.kotlin.core.publisher.switchIfEmpty
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

interface AccountBalanceService {
  fun getBalance(accountId: Long): Mono<BigDecimal>
  fun getBalanceAtTime(accountId: Long, time: LocalDateTime): Mono<BigDecimal>
}

@Component
class DefaultAccountBalanceService(
  private val accountBalanceRepository: AccountBalanceRepository,
  private val accountBalanceSnapshotRepository: AccountBalanceSnapshotRepository,
  private val eventStoreRepository: EventStoreRepository,
  private val metrics: QueryMetrics = QueryMetrics.NOOP,
) : AccountBalanceService {
  private val log = LoggerFactory.getLogger(DefaultAccountBalanceService::class.java)
  private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  override fun getBalance(accountId: Long): Mono<BigDecimal> {
    val started = System.nanoTime()
    return accountBalanceRepository.findById(accountId)
      .map(AccountBalance::balance)
      .doOnNext { log.info("Balance=$it for accountId=$accountId") }
      .doOnSuccess {
        metrics.recordBalanceQuery(Duration.ofNanos(System.nanoTime() - started))
      }
  }

  override fun getBalanceAtTime(
    accountId: Long,
    time: LocalDateTime
  ): Mono<BigDecimal> {
    val started = System.nanoTime()
    return accountBalanceSnapshotRepository.findFirstByAccountIdAndCreatedAtIsBeforeOrderByCreatedAtDesc(
      accountId,
      time
    )
      .flatMap { snapshot -> getBalanceAtTimeBySnapshot(snapshot, time, started) }
      .switchIfEmpty { getBalanceAtTimeByFullHistory(accountId, time, started) }
      .doOnNext { log.info("Balance=$it for accountId=$accountId at time=$time") }
  }

  fun getBalanceAtTimeBySnapshot(
    snapshot: AccountBalanceSnapshot,
    time: LocalDateTime,
    started: Long = System.nanoTime(),
  ): Mono<BigDecimal> {
    log.info("Get balance at $time for accountId=${snapshot.accountId}. Found snapshot=$snapshot")
    val replayed = AtomicLong(0)
    return eventStoreRepository.findAllBankingEventByAggregateIdAndAggregateTypeAndCreatedAtAfterAndCreatedAtBeforeOrderByVersion(
      snapshot.accountId,
      AggregateType.ACCOUNT,
      snapshot.createdAt,
      time
    )
      .handle { event: BankingEvent, sink -> deserializeEvents(event, sink) }
      .doOnNext {
        replayed.incrementAndGet()
        log.info("Get balance at $time for accountId=${snapshot.accountId}. Apply event=$it")
      }
      .reduce(snapshot.balance) { acc, event -> event.applyBalance(acc) }
      .doOnSuccess {
        metrics.recordTemporalQuery(
          path = "snapshot",
          duration = Duration.ofNanos(System.nanoTime() - started),
          eventsReplayed = replayed.get(),
        )
      }
  }

  fun getBalanceAtTimeByFullHistory(
    accountId: Long,
    time: LocalDateTime,
    started: Long = System.nanoTime(),
  ): Mono<BigDecimal> {
    log.info("Get balance at $time for accountId=$accountId by full history")
    val replayed = AtomicLong(0)
    return eventStoreRepository.findAllBankingEventByAggregateIdAndAggregateTypeAndCreatedAtBeforeOrderByVersion(
      accountId,
      AggregateType.ACCOUNT,
      time
    )
      .handle { event: BankingEvent, sink -> deserializeEvents(event, sink) }
      .doOnNext {
        replayed.incrementAndGet()
        log.info("Get balance at $time for accountId=$accountId. Apply event=$it")
      }
      .reduce(BigDecimal.ZERO) { acc, event -> event.applyBalance(acc) }
      .doOnSuccess {
        metrics.recordTemporalQuery(
          path = "full_history",
          duration = Duration.ofNanos(System.nanoTime() - started),
          eventsReplayed = replayed.get(),
        )
      }
  }

  private fun deserializeEvents(
    event: BankingEvent,
    sink: SynchronousSink<AccountCommand>,
  ) {
    when (event.eventType) {
      EventType.MONEY_DEPOSITED -> sink.next(
        mapper.readValue(
          event.payload,
          AccountCommand.DepositMoney::class.java
        )
      )

      EventType.MONEY_WITHDRAWN -> sink.next(
        mapper.readValue(
          event.payload,
          AccountCommand.WithdrawMoney::class.java
        )
      )

      else -> log.info("Skip event type ${event.eventType}")
    }
  }
}
