package com.andver.outbox.consumer

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.repository.LockingOutboxRepository
import com.andver.outbox.consumer.service.DefaultIdempotentEventProcessor
import com.andver.outbox.consumer.service.IdempotentEventProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.EnableKafka

@EnableKafka
@AutoConfiguration
@ConditionalOnProperty(prefix = "outbox.consumer", name = ["enabled"], matchIfMissing = false)
class OutboxConsumerAutoConfiguration(
  private val lockingOutboxRepository: LockingOutboxRepository,
  private val eventHandlers: List<OutboxEventHandler>,
) {

  @Bean
  fun idempotentEventProcessor(): IdempotentEventProcessor {
    return DefaultIdempotentEventProcessor(lockingOutboxRepository)
  }

  @Bean
  fun outboxConsumer(processor: IdempotentEventProcessor): OutboxEventConsumer {
    return OutboxEventConsumer(eventHandlers, processor)
  }
}