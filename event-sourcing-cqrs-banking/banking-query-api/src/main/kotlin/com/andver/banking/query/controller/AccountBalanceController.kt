package com.andver.banking.query.controller

import com.andver.banking.query.service.AccountBalanceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/query")
class AccountBalanceController(
  private val accountBalanceService: AccountBalanceService,
) {
  @GetMapping("/account/{id}/balance")
  fun getAccountBalance(@PathVariable id: Long): Mono<BalanceResponse> {
    return accountBalanceService.getBalance(id)
      .map { balance -> BalanceResponse(balance) }
  }

  @PostMapping("/account/balance-at")
  fun getAccountBalanceAt(@RequestBody request: BalanceAtRequest): Mono<BalanceResponse> {
    return accountBalanceService.getBalanceAtTime(request.accountId, request.time)
      .map { balance -> BalanceResponse(balance) }
  }
}

data class BalanceAtRequest(
  val accountId: Long,
  val time: LocalDateTime,
)

data class BalanceResponse(
  val balance: BigDecimal,
)