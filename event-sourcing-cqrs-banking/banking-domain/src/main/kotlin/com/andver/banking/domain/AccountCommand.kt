package com.andver.banking.domain

import java.math.BigDecimal

sealed class AccountCommand {
  data class OpenAccount(val accountId: Long, val ownerId: Long) : AccountCommand() {
    override fun applyBalance(balance: BigDecimal): BigDecimal {
      return balance
    }
  }

  data class DepositMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand() {
    override fun applyBalance(balance: BigDecimal): BigDecimal {
      return balance.add(amount)
    }
  }

  data class WithdrawMoney(val accountId: Long, val amount: BigDecimal) : AccountCommand() {
    override fun applyBalance(balance: BigDecimal): BigDecimal {
      return balance.subtract(amount)
    }
  }

  abstract fun applyBalance(balance: BigDecimal): BigDecimal
}