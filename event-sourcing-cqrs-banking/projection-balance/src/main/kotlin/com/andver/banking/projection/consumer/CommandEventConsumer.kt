package com.andver.banking.projection.consumer

import com.andver.banking.projection.handler.BalanceUpdateHandler
import com.andver.banking.projection.model.BankingEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import reactor.core.scheduler.Schedulers

@Component
class CommandEventConsumer(
  private val balanceUpdateHandler: BalanceUpdateHandler
) {
  private val log = LoggerFactory.getLogger(CommandEventConsumer::class.java)

  @KafkaListener(topics = ["\${command-event.topic}"])
  fun consume(event: BankingEvent) {
    balanceUpdateHandler.handle(event)
      .doOnError { e -> log.error("Error while consuming record=$event", e) }
      .doOnSuccess { log.info("Received banking event =$event") }
      .subscribeOn(Schedulers.boundedElastic())
      .subscribe()
  }
}