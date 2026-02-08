package com.andver.banking.projection.handler

import com.andver.banking.domain.EventType
import com.andver.banking.projection.model.BankingEvent
import com.andver.banking.projection.repository.AccountBalanceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
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
) : BalanceUpdateHandler {

  private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

  override fun handle(event: BankingEvent): Mono<Void> {
    val updatedAt = LocalDateTime.ofInstant(
      Instant.ofEpochMilli(event.createdAt / 1000),
      ZoneOffset.UTC
    )
    return when (event.eventType) {
      EventType.ACCOUNT_OPENED -> {
        val payload = mapper.readTree(event.payload)
        val ownerId = payload[OWNER_ID].asLong()
        val updatedAt = updatedAt
        accountBalanceRepository.create(event.aggregateId, ownerId, BigDecimal(0.0), updatedAt)
      }

      EventType.MONEY_DEPOSITED -> {
        val payload = mapper.readTree(event.payload)
        val amount = BigDecimal(payload[AMOUNT].asText())
        val updatedAt = updatedAt
        accountBalanceRepository.findById(event.aggregateId)
          .flatMap { acc ->
            val newBalance = acc.balance + amount
            accountBalanceRepository.save(acc.copy(balance = newBalance, updatedAt = updatedAt))
          }
      }

      EventType.MONEY_WITHDRAWN -> {
        val payload = mapper.readTree(event.payload)
        val amount = BigDecimal(payload[AMOUNT].asText())
        val updatedAt = updatedAt
        accountBalanceRepository.findById(event.aggregateId)
          .flatMap { acc ->
            val newBalance = acc.balance - amount
            accountBalanceRepository.save(acc.copy(balance = newBalance, updatedAt = updatedAt))
          }
      }
    }.then()
  }
}
