package com.andver.idempotency.demo.charge.service

import com.andver.idempotency.demo.charge.repository.LedgerRepository
import com.andver.idempotency.server.service.IdempotencyCompleter
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

@Service
class ChargeService(
  private val ledgerRepository: LedgerRepository,
  private val completer: IdempotencyCompleter,
  private val transactionalOperator: TransactionalOperator,
  private val objectMapper: ObjectMapper,
) {

  fun charge(exchange: ServerWebExchange, ownerId: String, amount: Long, delayMs: Long): Mono<ChargeOutcome> {
    val work = Mono.defer {
      if (amount <= 0) {
        val body = errorBody("amount must be positive")
        return@defer completer.complete(exchange, 400, body)
          .thenReturn(ChargeOutcome(400, body))
      }
      val chargeId = UUID.randomUUID()
      ledgerRepository.debit(ownerId, amount, chargeId)
        .flatMap { balance ->
          val body = objectMapper.writeValueAsString(
            mapOf(
              "chargeId" to chargeId.toString(),
              "ownerId" to ownerId,
              "amount" to amount,
              "balance" to balance,
            ),
          )
          completer.complete(exchange, 200, body).thenReturn(ChargeOutcome(200, body))
        }
        .switchIfEmpty(
          Mono.defer {
            val body = errorBody("insufficient funds")
            completer.complete(exchange, 422, body).thenReturn(ChargeOutcome(422, body))
          },
        )
    }

    // Commit first, then optionally stall the HTTP response so the client can time out
    // while the server already has a completed idempotent result to replay.
    return transactionalOperator.transactional(work)
      .transformDeferred { mono ->
        if (delayMs > 0) mono.delayElement(Duration.ofMillis(delayMs)) else mono
      }
  }

  private fun errorBody(message: String): String =
    objectMapper.writeValueAsString(mapOf("error" to message))
}

data class ChargeOutcome(
  val status: Int,
  val body: String,
)
