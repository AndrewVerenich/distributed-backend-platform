package com.andver.banking.command.model

import java.math.BigDecimal

sealed class AccountCommand {
  data class OpenAccount(val accountId: Long, val owner: String) : AccountCommand()
  data class DepositMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand()
  data class WithdrawMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand()
}