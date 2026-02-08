package com.andver.banking.domain

enum class AggregateType {
  ACCOUNT;

  companion object {
    fun from(command: AccountCommand): AggregateType = when (command) {
      is AccountCommand -> ACCOUNT
    }
  }
}

