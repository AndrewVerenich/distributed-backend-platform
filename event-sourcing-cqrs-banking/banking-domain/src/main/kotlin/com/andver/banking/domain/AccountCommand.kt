package com.andver.banking.domain

import java.math.BigDecimal

sealed class AccountCommand {
  data class OpenAccount(val accountId: Long, val ownerId: Long) : AccountCommand()
  data class DepositMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand()
  data class WithdrawMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand()
}