package com.andver.banking.command.handler

import com.andver.banking.command.repository.EventStoreRepository
import com.andver.banking.domain.AccountCommand
import com.andver.banking.domain.AggregateType
import com.andver.banking.domain.EventType
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.time.LocalDateTime
import java.util.*

interface CommandHandler {
  fun handle(cmd: AccountCommand): Mono<Void>
}

@Component
class DefaultCommandHandler(
  private val eventStoreRepository: EventStoreRepository,
  private val commandSerializer: CommandSerializer,
) : CommandHandler {
  override fun handle(cmd: AccountCommand): Mono<Void> {
    return when (cmd) {
      is AccountCommand.OpenAccount -> handleOpenAccount(cmd)
      is AccountCommand.DepositMoney -> handleBalanceChange(cmd, cmd.accountId)
      is AccountCommand.WithdrawMoney -> handleBalanceChange(cmd, cmd.accountId)
    }
  }

  private fun handleBalanceChange(cmd: AccountCommand, accountId: Long): Mono<Void> {
    return eventStoreRepository.findVersionByAggregateId(AggregateType.from(cmd), accountId)
      .flatMap { version ->
        if (version == 0L) {
          return@flatMap Mono.error(IllegalStateException("Account does not exist: $accountId"))
        }
        eventStoreRepository.saveWithJsonb(
          eventId = UUID.randomUUID(),
          aggregateId = accountId,
          aggregateType = AggregateType.from(cmd),
          eventType = EventType.from(cmd),
          payload = commandSerializer.serialize(cmd),
          version = version + 1,
          createdAt = LocalDateTime.now(),
        ).then()
      }
  }

  private fun handleOpenAccount(cmd: AccountCommand.OpenAccount): Mono<Void> {
    return eventStoreRepository.findVersionByAggregateId(AggregateType.from(cmd), cmd.accountId)
      .filter { version -> version > 0 }
      .flatMap<Void> { Mono.error(IllegalStateException("Account already exists: ${cmd.accountId}")) }
      .switchIfEmpty {
        eventStoreRepository.saveWithJsonb(
          eventId = UUID.randomUUID(),
          aggregateId = cmd.accountId,
          aggregateType = AggregateType.from(cmd),
          eventType = EventType.from(cmd),
          payload = commandSerializer.serialize(cmd),
          version = 1,
          createdAt = LocalDateTime.now(),
        ).then()
      }
  }
}