package com.andver.banking.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AccountCommandTest {

  @Test
  fun `OpenAccount applyBalance returns unchanged balance`() {
    val cmd = AccountCommand.OpenAccount(accountId = 1L, ownerId = 42L)
    val initial = BigDecimal("1000.00")

    assertThat(cmd.applyBalance(initial)).isEqualByComparingTo(initial)
  }

  @Test
  fun `OpenAccount applyBalance works on zero balance`() {
    val cmd = AccountCommand.OpenAccount(accountId = 1L, ownerId = 1L)

    assertThat(cmd.applyBalance(BigDecimal.ZERO)).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `DepositMoney applyBalance adds deposit amount to balance`() {
    val cmd = AccountCommand.DepositMoney(accountId = 1L, amount = BigDecimal("500.00"))
    val balance = BigDecimal("1000.00")

    assertThat(cmd.applyBalance(balance)).isEqualByComparingTo(BigDecimal("1500.00"))
  }

  @Test
  fun `DepositMoney applyBalance adds to zero balance`() {
    val cmd = AccountCommand.DepositMoney(accountId = 1L, amount = BigDecimal("250.50"))

    assertThat(cmd.applyBalance(BigDecimal.ZERO)).isEqualByComparingTo(BigDecimal("250.50"))
  }

  @Test
  fun `DepositMoney applyBalance handles fractional amounts precisely`() {
    val cmd = AccountCommand.DepositMoney(accountId = 1L, amount = BigDecimal("0.01"))
    val balance = BigDecimal("99.99")

    assertThat(cmd.applyBalance(balance)).isEqualByComparingTo(BigDecimal("100.00"))
  }

  @Test
  fun `WithdrawMoney applyBalance subtracts withdrawal from balance`() {
    val cmd = AccountCommand.WithdrawMoney(accountId = 1L, amount = BigDecimal("200.00"))
    val balance = BigDecimal("1000.00")

    assertThat(cmd.applyBalance(balance)).isEqualByComparingTo(BigDecimal("800.00"))
  }

  @Test
  fun `WithdrawMoney applyBalance produces negative balance when overdraft`() {
    val cmd = AccountCommand.WithdrawMoney(accountId = 1L, amount = BigDecimal("1500.00"))
    val balance = BigDecimal("1000.00")

    assertThat(cmd.applyBalance(balance)).isEqualByComparingTo(BigDecimal("-500.00"))
  }

  @Test
  fun `WithdrawMoney applyBalance handles exact withdrawal leaving zero`() {
    val cmd = AccountCommand.WithdrawMoney(accountId = 1L, amount = BigDecimal("100.00"))

    assertThat(cmd.applyBalance(BigDecimal("100.00"))).isEqualByComparingTo(BigDecimal.ZERO)
  }

  @Test
  fun `EventType from OpenAccount returns ACCOUNT_OPENED`() {
    assertThat(EventType.from(AccountCommand.OpenAccount(1L, 1L))).isEqualTo(EventType.ACCOUNT_OPENED)
  }

  @Test
  fun `EventType from DepositMoney returns MONEY_DEPOSITED`() {
    assertThat(EventType.from(AccountCommand.DepositMoney(1L, BigDecimal.ONE))).isEqualTo(EventType.MONEY_DEPOSITED)
  }

  @Test
  fun `EventType from WithdrawMoney returns MONEY_WITHDRAWN`() {
    assertThat(EventType.from(AccountCommand.WithdrawMoney(1L, BigDecimal.ONE))).isEqualTo(EventType.MONEY_WITHDRAWN)
  }

  @Test
  fun `AggregateType from any AccountCommand returns ACCOUNT`() {
    assertThat(AggregateType.from(AccountCommand.OpenAccount(1L, 1L))).isEqualTo(AggregateType.ACCOUNT)
    assertThat(AggregateType.from(AccountCommand.DepositMoney(1L, BigDecimal.TEN))).isEqualTo(AggregateType.ACCOUNT)
    assertThat(AggregateType.from(AccountCommand.WithdrawMoney(1L, BigDecimal.TEN))).isEqualTo(AggregateType.ACCOUNT)
  }
}
