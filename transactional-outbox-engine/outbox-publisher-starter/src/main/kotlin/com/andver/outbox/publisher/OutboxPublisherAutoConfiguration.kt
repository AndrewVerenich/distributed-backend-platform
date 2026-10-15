package com.andver.outbox.publisher

import com.andver.outbox.publisher.metrics.MicrometerOutboxPublisherMetrics
import com.andver.outbox.publisher.metrics.OutboxPublisherMetrics
import com.andver.outbox.publisher.repository.WriteOutboxRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@AutoConfiguration
@EnableR2dbcRepositories(basePackages = ["com.andver.outbox.publisher.repository"])
class OutboxPublisherAutoConfiguration(
  private val writeOutboxRepository: WriteOutboxRepository,
) {

  @Bean
  @ConditionalOnMissingBean(OutboxPublisherMetrics::class)
  fun outboxPublisherMetrics(registry: ObjectProvider<MeterRegistry>): OutboxPublisherMetrics {
    return registry.getIfAvailable()?.let { MicrometerOutboxPublisherMetrics(it) }
      ?: OutboxPublisherMetrics.NOOP
  }

  @Bean
  fun outboxPublisher(metrics: OutboxPublisherMetrics): OutboxPublisher {
    return DefaultOutboxPublisher(
      repository = writeOutboxRepository,
      metrics = metrics,
    )
  }
}
