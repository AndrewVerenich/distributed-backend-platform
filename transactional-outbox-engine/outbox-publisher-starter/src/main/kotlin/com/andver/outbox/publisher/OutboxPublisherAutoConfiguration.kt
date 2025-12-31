package com.andver.outbox.publisher

import com.andver.outbox.publisher.repository.WriteOutboxRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@AutoConfiguration
@EnableR2dbcRepositories(basePackages = ["com.andver.outbox.publisher.repository"])
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