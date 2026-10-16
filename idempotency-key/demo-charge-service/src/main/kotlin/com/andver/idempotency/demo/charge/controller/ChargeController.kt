package com.andver.idempotency.demo.charge.controller

import com.andver.idempotency.api.IdempotencyHeaders
import com.andver.idempotency.demo.charge.repository.LedgerRepository
import com.andver.idempotency.demo.charge.service.ChargeService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
class ChargeController(
  private val chargeService: ChargeService,
  private val ledgerRepository: LedgerRepository,
) {

  @PostMapping("/charges")
  fun charge(
    exchange: ServerWebExchange,
    @RequestHeader(IdempotencyHeaders.OWNER) ownerId: String,
    @RequestBody request: ChargeRequest,
    @RequestParam(defaultValue = "0") delayMs: Long,
  ): Mono<ResponseEntity<String>> =
    chargeService.charge(exchange, ownerId, request.amount, delayMs)
      .map { outcome ->
        ResponseEntity.status(outcome.status)
          .contentType(MediaType.APPLICATION_JSON)
          .body(outcome.body)
      }

  @GetMapping("/accounts/{ownerId}")
  fun account(@PathVariable ownerId: String): Mono<Map<String, Any>> =
    Mono.zip(
      ledgerRepository.balance(ownerId).defaultIfEmpty(0),
      ledgerRepository.countCharges(ownerId).defaultIfEmpty(0),
    ).map { tuple ->
      mapOf(
        "ownerId" to ownerId,
        "balance" to tuple.t1,
        "charges" to tuple.t2,
      )
    }

  @PostMapping("/demo/reset")
  fun reset(
    @RequestParam(defaultValue = "alice") ownerId: String,
    @RequestParam(defaultValue = "100000") balance: Long,
  ): Mono<Map<String, Any>> =
    ledgerRepository.reset(ownerId, balance)
      .thenReturn(mapOf("ownerId" to ownerId, "balance" to balance, "charges" to 0))
}

data class ChargeRequest(
  val amount: Long,
)
