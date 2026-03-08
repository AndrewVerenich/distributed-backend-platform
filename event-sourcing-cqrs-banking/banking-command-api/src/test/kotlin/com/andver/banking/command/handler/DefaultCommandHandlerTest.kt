package com.andver.banking.command.handler

import com.andver.banking.command.repository.EventStoreRepository
import com.andver.banking.domain.AccountCommand
import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.util.*

class DefaultCommandHandlerTest {

  private val repository = mockk<EventStoreRepository>()
  private val serializer = DefaultCommandSerializer()
  private lateinit var handler: DefaultCommandHandler

  @BeforeEach
  fun setUp() {
    handler = DefaultCommandHandler(repository, serializer)
  }

  @Test
  fun `handle OpenAccount saves event with version 1 when account does not exist`() {
    val cmd = AccountCommand.OpenAccount(accountId = 100L, ownerId = 42L)
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 100L)
    } returns Mono.just(0L)
    every {
      repository.saveWithJsonb(
        any(),
        eq(100L),
        eq(AggregateType.ACCOUNT),
        eq(EventType.ACCOUNT_OPENED),
        any(),
        eq(1L),
        any()
      )
    } returns Mono.empty()

    StepVerifier.create(handler.handle(cmd))
      .verifyComplete()

    verify {
      repository.saveWithJsonb(
        any(), eq(100L), eq(AggregateType.ACCOUNT), eq(EventType.ACCOUNT_OPENED), any(), eq(1L), any()
      )
    }
  }

  @Test
  fun `handle OpenAccount rejects duplicate account`() {
    val cmd = AccountCommand.OpenAccount(accountId = 200L, ownerId = 1L)
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 200L)
    } returns Mono.just(3L)

    StepVerifier.create(handler.handle(cmd))
      .expectErrorMatches {
        it is IllegalStateException && it.message!!.contains("Account already exists")
      }
      .verify()
  }

  @Test
  fun `handle DepositMoney saves event with incremented version`() {
    val cmd = AccountCommand.DepositMoney(accountId = 300L, amount = BigDecimal("100.00"))
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 300L)
    } returns Mono.just(2L)
    every {
      repository.saveWithJsonb(
        any(),
        eq(300L),
        eq(AggregateType.ACCOUNT),
        eq(EventType.MONEY_DEPOSITED),
        any(),
        eq(3L),
        any()
      )
    } returns Mono.empty()

    StepVerifier.create(handler.handle(cmd))
      .verifyComplete()

    verify {
      repository.saveWithJsonb(
        any(), eq(300L), eq(AggregateType.ACCOUNT), eq(EventType.MONEY_DEPOSITED), any(), eq(3L), any()
      )
    }
  }

  @Test
  fun `handle DepositMoney rejects when account does not exist (version 0)`() {
    val cmd = AccountCommand.DepositMoney(accountId = 999L, amount = BigDecimal("50.00"))
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 999L)
    } returns Mono.just(0L)

    StepVerifier.create(handler.handle(cmd))
      .expectErrorMatches {
        it is IllegalStateException && it.message!!.contains("Account does not exist: 999")
      }
      .verify()
  }

  @Test
  fun `handle WithdrawMoney saves event with correct event type`() {
    val cmd = AccountCommand.WithdrawMoney(accountId = 400L, amount = BigDecimal("50.00"))
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 400L)
    } returns Mono.just(5L)
    every {
      repository.saveWithJsonb(
        any(),
        eq(400L),
        eq(AggregateType.ACCOUNT),
        eq(EventType.MONEY_WITHDRAWN),
        any(),
        eq(6L),
        any()
      )
    } returns Mono.empty()

    StepVerifier.create(handler.handle(cmd))
      .verifyComplete()

    verify {
      repository.saveWithJsonb(
        any(), eq(400L), eq(AggregateType.ACCOUNT), eq(EventType.MONEY_WITHDRAWN), any(), eq(6L), any()
      )
    }
  }

  @Test
  fun `handle WithdrawMoney rejects when account does not exist`() {
    val cmd = AccountCommand.WithdrawMoney(accountId = 888L, amount = BigDecimal("100.00"))
    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 888L)
    } returns Mono.just(0L)

    StepVerifier.create(handler.handle(cmd))
      .expectErrorMatches {
        it is IllegalStateException && it.message!!.contains("Account does not exist: 888")
      }
      .verify()
  }

  @Test
  fun `saved payload contains serialized command fields`() {
    val cmd = AccountCommand.DepositMoney(accountId = 500L, amount = BigDecimal("75.00"))
    val payloadSlot = slot<String>()

    every {
      repository.findVersionByAggregateId(AggregateType.ACCOUNT, 500L)
    } returns Mono.just(1L)
    every {
      repository.saveWithJsonb(any(), any(), any(), any(), capture(payloadSlot), any(), any())
    } returns Mono.empty()

    StepVerifier.create(handler.handle(cmd)).verifyComplete()

    assertThat(payloadSlot.captured).contains("500")
    assertThat(payloadSlot.captured).contains("75")
  }

  @Test
  fun `each handle invocation generates a unique eventId`() {
    val idSlot1 = slot<UUID>()
    val idSlot2 = slot<UUID>()

    val cmd1 = AccountCommand.OpenAccount(accountId = 1L, ownerId = 1L)
    val cmd2 = AccountCommand.OpenAccount(accountId = 2L, ownerId = 2L)

    every { repository.findVersionByAggregateId(AggregateType.ACCOUNT, 1L) } returns Mono.just(0L)
    every { repository.findVersionByAggregateId(AggregateType.ACCOUNT, 2L) } returns Mono.just(0L)
    every {
      repository.saveWithJsonb(capture(idSlot1), eq(1L), any(), any(), any(), any(), any())
    } returns Mono.empty()
    every {
      repository.saveWithJsonb(capture(idSlot2), eq(2L), any(), any(), any(), any(), any())
    } returns Mono.empty()

    handler.handle(cmd1).block()
    handler.handle(cmd2).block()

    assertThat(idSlot1.captured).isNotEqualTo(idSlot2.captured)
  }
}
