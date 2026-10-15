package com.andver.outbox.consumer

import com.andver.outbox.consumer.handler.OutboxEventHandler
import com.andver.outbox.consumer.metrics.MicrometerOutboxConsumerMetrics
import com.andver.outbox.consumer.metrics.OutboxConsumerMetrics
import com.andver.outbox.consumer.repository.LockingOutboxRepository
import com.andver.outbox.consumer.service.DefaultIdempotentEventProcessor
import com.andver.outbox.consumer.service.IdempotentEventProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.kafka.annotation.EnableKafka

@AutoConfiguration
@EnableR2dbcRepositories(basePackages = ["com.andver.outbox.consumer.repository"])
@EnableKafka
@ConditionalOnProperty(prefix = "outbox.consumer", name = ["enabled"], matchIfMissing = false)
class OutboxConsumerAutoConfiguration(
  private val lockingOutboxRepository: LockingOutboxRepository,
  private val eventHandlers: List<OutboxEventHandler>,
) {

  @Bean
  @ConditionalOnMissingBean(OutboxConsumerMetrics::class)
  fun outboxConsumerMetrics(registry: ObjectProvider<MeterRegistry>): OutboxConsumerMetrics {
    return registry.getIfAvailable()?.let { MicrometerOutboxConsumerMetrics(it) }
      ?: OutboxConsumerMetrics.NOOP
  }

  @Bean
  fun idempotentEventProcessor(metrics: OutboxConsumerMetrics): IdempotentEventProcessor {
    return DefaultIdempotentEventProcessor(lockingOutboxRepository, metrics)
  }

  @Bean
  fun outboxConsumer(
    processor: IdempotentEventProcessor,
    metrics: OutboxConsumerMetrics,
  ): OutboxEventConsumer {
    return OutboxEventConsumer(eventHandlers, processor, metrics)
  }
}
