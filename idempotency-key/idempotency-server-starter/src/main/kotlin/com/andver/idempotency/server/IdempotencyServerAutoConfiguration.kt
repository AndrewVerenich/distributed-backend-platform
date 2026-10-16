package com.andver.idempotency.server

import com.andver.idempotency.server.filter.IdempotencyWebFilter
import com.andver.idempotency.server.metrics.IdempotencyServerMetrics
import com.andver.idempotency.server.metrics.MicrometerIdempotencyServerMetrics
import com.andver.idempotency.server.metrics.NoopIdempotencyServerMetrics
import com.andver.idempotency.server.owner.HeaderOwnerResolver
import com.andver.idempotency.server.owner.OwnerResolver
import com.andver.idempotency.server.repository.IdempotencyRepository
import com.andver.idempotency.server.service.IdempotencyClaimService
import com.andver.idempotency.server.service.IdempotencyCompleter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Clock

@AutoConfiguration
@ConditionalOnClass(DatabaseClient::class)
@ConditionalOnProperty(prefix = "idempotency.server", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotencyServerProperties::class)
class IdempotencyServerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyClock(): Clock = Clock.systemUTC()

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyRepository(databaseClient: DatabaseClient): IdempotencyRepository =
    IdempotencyRepository(databaseClient)

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyServerMetrics(meterRegistry: ObjectProvider<MeterRegistry>): IdempotencyServerMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerIdempotencyServerMetrics(registry) else NoopIdempotencyServerMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun ownerResolver(properties: IdempotencyServerProperties): OwnerResolver =
    HeaderOwnerResolver(properties)

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyClaimService(
    repository: IdempotencyRepository,
    properties: IdempotencyServerProperties,
    metrics: IdempotencyServerMetrics,
    clock: Clock,
  ): IdempotencyClaimService =
    IdempotencyClaimService(repository, properties, metrics, clock)

  @Bean
  @ConditionalOnMissingBean
  fun idempotencyCompleter(claimService: IdempotencyClaimService): IdempotencyCompleter =
    IdempotencyCompleter(claimService)

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(IdempotencyClaimService::class)
  fun idempotencyWebFilter(
    properties: IdempotencyServerProperties,
    claimService: IdempotencyClaimService,
    ownerResolver: OwnerResolver,
    metrics: IdempotencyServerMetrics,
  ): IdempotencyWebFilter =
    IdempotencyWebFilter(properties, claimService, ownerResolver, metrics)
}
