package com.andver.outbox.publisher

import com.andver.outbox.publisher.repository.WriteOutboxRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
class OutboxPublisherAutoConfiguration(
  private val writeOutboxRepository: WriteOutboxRepository,
) {

  @Bean
  fun outboxPublisher(): OutboxPublisher {
    return DefaultOutboxPublisher(
      repository = writeOutboxRepository,
    )
  }
}