package com.andver.idempotency.client

import com.andver.idempotency.client.filter.IdempotencyExchangeFilter
import com.andver.idempotency.client.filter.IdempotencyWebClientCustomizer
import com.andver.idempotency.client.metrics.IdempotencyClientMetrics
import com.andver.idempotency.client.metrics.MicrometerIdempotencyClientMetrics
import com.andver.idempotency.client.metrics.NoopIdempotencyClientMetrics
import com.andver.idempotency.client.store.InMemoryOperationKeyStore
import com.andver.idempotency.client.store.OperationKeyStore
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient
import java.time.Clock

@AutoConfiguration
@ConditionalOnClass(WebClient::class)
@ConditionalOnProperty(prefix = "idempotency.client", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotencyClientProperties::class)
class IdempotencyClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = ["idempotencyClientClock"])
  fun idempotencyClientClock(): Clock = Clock.systemUTC()

  @Bean
  @ConditionalOnMissingBean
  fun operationKeyStore(
    properties: IdempotencyClientProperties,
    idempotencyClientClock: Clock,
  ): OperationKeyStore = InMemoryOperationKeyStore(properties, idempotencyClientClock)

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyClientMetrics(meterRegistry: ObjectProvider<MeterRegistry>): IdempotencyClientMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerIdempotencyClientMetrics(registry) else NoopIdempotencyClientMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyExchangeFilter(
    keyStore: OperationKeyStore,
    properties: IdempotencyClientProperties,
    metrics: IdempotencyClientMetrics,
  ): IdempotencyExchangeFilter = IdempotencyExchangeFilter(keyStore, properties, metrics)

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyWebClientCustomizer(filter: IdempotencyExchangeFilter): WebClientCustomizer =
    IdempotencyWebClientCustomizer(filter)
}
