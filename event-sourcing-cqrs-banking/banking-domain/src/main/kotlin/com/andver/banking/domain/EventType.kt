package com.andver.banking.domain

enum class EventType {
  ACCOUNT_OPENED,
  MONEY_DEPOSITED,
  MONEY_WITHDRAWN;

  companion object {
    fun from(command: AccountCommand): EventType = when (command) {
      is AccountCommand.OpenAccount -> ACCOUNT_OPENED
      is AccountCommand.DepositMoney -> MONEY_DEPOSITED
      is AccountCommand.WithdrawMoney -> MONEY_WITHDRAWN
    }
  }
}


