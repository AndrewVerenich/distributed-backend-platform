package com.andver.banking.command.controller

import com.andver.banking.command.handler.CommandHandler
import com.andver.banking.command.model.AccountCommand
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/commands")
class AccountCommandController(
  private val handler: CommandHandler,
) {
  @PostMapping("/open")
  fun openAccount(@RequestBody req: OpenAccountRequest): Mono<Void> {
    return handler.handle(
      AccountCommand.OpenAccount(
        req.accountId,
        req.owner
      )
    )
  }

  @PostMapping("/deposit")
  fun deposit(@RequestBody req: DepositRequest): Mono<Void> {
    return handler.handle(
      AccountCommand.DepositMoney(
        req.accountId,
        req.amount
      )
    )
  }

  @PostMapping("/withdraw")
  fun withdraw(@RequestBody req: WithdrawRequest): Mono<Void> {
    return handler.handle(
      AccountCommand.WithdrawMoney(
        req.accountId,
        req.amount
      )
    )
  }
}

data class OpenAccountRequest(val accountId: Long, val owner: String)
data class DepositRequest(val accountId: Long, val amount: BigDecimal)
data class WithdrawRequest(val accountId: Long, val amount: BigDecimal)