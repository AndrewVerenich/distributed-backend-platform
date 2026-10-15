package com.andver.circuitbreaker

import com.andver.circuitbreaker.config.CircuitBreakerProperties
import com.andver.circuitbreaker.core.CircuitBreakerRegistry
import com.andver.circuitbreaker.core.NanoClock
import com.andver.circuitbreaker.filter.WebClientCircuitBreakerFilter
import com.andver.circuitbreaker.metrics.CircuitBreakerMetrics
import com.andver.circuitbreaker.metrics.MicrometerCircuitBreakerMetrics
import com.andver.circuitbreaker.metrics.NoopCircuitBreakerMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient

@AutoConfiguration
@ConditionalOnClass(WebClient::class)
@ConditionalOnProperty(prefix = "circuit-breaker", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CircuitBreakerProperties::class)
class CircuitBreakerAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun circuitBreakerMetrics(meterRegistry: ObjectProvider<MeterRegistry>): CircuitBreakerMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerCircuitBreakerMetrics(registry) else NoopCircuitBreakerMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun circuitBreakerNanoClock(): NanoClock = NanoClock.SYSTEM

  @Bean
  @ConditionalOnMissingBean
  fun circuitBreakerRegistry(
    properties: CircuitBreakerProperties,
    metrics: CircuitBreakerMetrics,
    clock: NanoClock,
  ): CircuitBreakerRegistry =
    CircuitBreakerRegistry(
      configs = properties.resolveConfigs(),
      defaultConfig = properties.defaultConfig(),
      clock = clock,
      metrics = metrics,
    )

  @Bean
  @ConditionalOnMissingBean
  fun webClientCircuitBreakerFilter(
    registry: CircuitBreakerRegistry,
    clock: NanoClock,
  ): WebClientCircuitBreakerFilter = WebClientCircuitBreakerFilter(registry, clock)
}
