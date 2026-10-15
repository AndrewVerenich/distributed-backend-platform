package com.andver.banking.projection.handler

import com.andver.banking.domain.EventType
import com.andver.banking.domain.entity.AccountBalance
import com.andver.banking.projection.metrics.ProjectionMetrics
import com.andver.banking.projection.model.BankingEvent
import com.andver.banking.projection.repository.AccountBalanceRepository
import com.andver.banking.projection.repository.AccountBalanceSnapshotRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

interface BalanceUpdateHandler {
  fun handle(event: BankingEvent): Mono<Void>
}

private const val AMOUNT = "amount"
private const val OWNER_ID = "ownerId"

@Component
class DefaultBalanceUpdateHandler(
  private val accountBalanceRepository: AccountBalanceRepository,
  private val accountBalanceSnapshotRepository: AccountBalanceSnapshotRepository,
  @Value("\${command-event.snapshot.every-events}")
  private val snapshotEveryEvent: Long,
  private val metrics: ProjectionMetrics,
) : BalanceUpdateHandler {

  private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  override fun handle(event: BankingEvent): Mono<Void> {
    val started = System.nanoTime()
    val updatedAt = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(event.createdAt),
      ZoneOffset.UTC
    )
    return when (event.eventType) {
      EventType.ACCOUNT_OPENED -> {
        val payload = mapper.readTree(event.payload)
        val ownerId = payload[OWNER_ID].asLong()
        accountBalanceRepository.create(event.aggregateId, ownerId, BigDecimal(0.0), updatedAt)
      }

      EventType.MONEY_DEPOSITED -> {
        val payload = mapper.readTree(event.payload)
        val amount = BigDecimal(payload[AMOUNT].asText())
        accountBalanceRepository.findById(event.aggregateId)
          .flatMap { acc ->
            val newBalance = acc.balance + amount
            accountBalanceRepository.save(acc.copy(balance = newBalance, updatedAt = updatedAt))
              .flatMap { createSnapshotIfNeeded(it, event.version) }
          }
      }

      EventType.MONEY_WITHDRAWN -> {
        val payload = mapper.readTree(event.payload)
        val amount = BigDecimal(payload[AMOUNT].asText())
        accountBalanceRepository.findById(event.aggregateId)
          .flatMap { acc ->
            val newBalance = acc.balance - amount
            accountBalanceRepository.save(acc.copy(balance = newBalance, updatedAt = updatedAt))
              .flatMap { createSnapshotIfNeeded(it, event.version) }
          }
      }
    }
      .doFinally { signal ->
        if (signal == reactor.core.publisher.SignalType.ON_COMPLETE) {
          metrics.recordApplied(event.eventType.name, Duration.ofNanos(System.nanoTime() - started))
        } else if (signal == reactor.core.publisher.SignalType.ON_ERROR) {
          metrics.recordError(event.eventType.name)
        }
      }
      .then()
  }

  fun createSnapshotIfNeeded(balance: AccountBalance, version: Long): Mono<Void> {
    return Mono.justOrEmpty(version)
      .filter { it % snapshotEveryEvent == 0L }
      .flatMap {
        accountBalanceSnapshotRepository.create(balance.id!!, balance.balance, balance.updatedAt)
          .doOnSuccess { metrics.recordSnapshot() }
      }
  }
}
