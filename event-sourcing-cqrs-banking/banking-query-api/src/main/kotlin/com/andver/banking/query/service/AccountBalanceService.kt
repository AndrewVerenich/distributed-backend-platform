package com.andver.banking.query.service

import com.andver.banking.domain.AccountCommand
import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import com.andver.banking.query.model.AccountBalance
import com.andver.banking.query.model.BankingEvent
import com.andver.banking.query.repository.AccountBalanceRepository
import com.andver.banking.query.repository.EventStoreRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime

interface AccountBalanceService {
  fun getBalance(accountId: Long): Mono<BigDecimal>
  fun getBalanceAtTime(accountId: Long, time: LocalDateTime): Mono<BigDecimal>
}

@Component
class DefaultAccountBalanceService(
  private val accountBalanceRepository: AccountBalanceRepository,
  private val eventStoreRepository: EventStoreRepository,
) : AccountBalanceService {
  private val log = LoggerFactory.getLogger(DefaultAccountBalanceService::class.java)
  private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  override fun getBalance(accountId: Long): Mono<BigDecimal> {
    return accountBalanceRepository.findById(accountId)
      .map(AccountBalance::balance)
      .doOnNext { log.info("Balance=$it for accountId=$accountId") }
  }

  override fun getBalanceAtTime(
    accountId: Long,
    time: LocalDateTime
  ): Mono<BigDecimal> {
    return eventStoreRepository.findAllBankingEventByAggregateIdAndAggregateTypeAndCreatedAtBeforeOrderByVersion(
      accountId,
      AggregateType.ACCOUNT,
      time
    )
      .filter { event -> event.eventType in BALANCE_CHANGE_EVENTS }
      .handle { event: BankingEvent, sink ->
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

          else -> sink.error(IllegalArgumentException("Unexpected event type: ${event.eventType}"))
        }
      }
      .reduce(BigDecimal.ZERO) { acc, event -> event.applyBalance(acc) }
  }

  private companion object {
    private val BALANCE_CHANGE_EVENTS = listOf(EventType.MONEY_DEPOSITED, EventType.MONEY_WITHDRAWN)
  }
}