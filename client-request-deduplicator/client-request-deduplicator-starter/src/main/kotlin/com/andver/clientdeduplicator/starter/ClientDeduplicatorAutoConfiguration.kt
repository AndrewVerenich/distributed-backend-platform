package com.andver.clientdeduplicator.starter

import com.andver.clientdeduplicator.starter.cache.CacheClient
import com.andver.clientdeduplicator.starter.cache.CacheRuleMatcher
import com.andver.clientdeduplicator.starter.cache.DefaultCacheRuleMatcher
import com.andver.clientdeduplicator.starter.cache.RedisCacheClient
import com.andver.clientdeduplicator.starter.filter.DeduplicationWebClientCustomizer
import com.andver.clientdeduplicator.starter.filter.WebClientDeduplicationFilter
import com.andver.clientdeduplicator.starter.fingerprint.DefaultFingerprintGenerator
import com.andver.clientdeduplicator.starter.fingerprint.FingerprintGenerator
import com.andver.clientdeduplicator.starter.metrics.DeduplicatorMetrics
import com.andver.clientdeduplicator.starter.metrics.DefaultDeduplicatorMetrics
import com.andver.clientdeduplicator.starter.properties.CacheProperties
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveRedisTemplate

@AutoConfiguration
@EnableConfigurationProperties(CacheProperties::class)
class ClientDeduplicatorAutoConfiguration(
  private val redisTemplate: ReactiveRedisTemplate<String, String>,
  private val cacheProperties: CacheProperties,
  private val meterRegistry: MeterRegistry,
) {

  @Bean
  fun webClientDeduplicationFilter(
    fingerprintGenerator: FingerprintGenerator,
    cacheClient: CacheClient,
    cacheRuleMatcher: CacheRuleMatcher,
    deduplicatorMetrics: DeduplicatorMetrics,
  ): WebClientDeduplicationFilter {
    return WebClientDeduplicationFilter(
      fingerprintGenerator = fingerprintGenerator,
      cacheClient = cacheClient,
      cacheProperties = cacheProperties,
      cacheRuleMatcher = cacheRuleMatcher,
      deduplicatorMetrics = deduplicatorMetrics,
    )
  }

  @Bean
  fun deduplicationWebClientCustomizer(
    filter: WebClientDeduplicationFilter,
  ): WebClientCustomizer {
    return DeduplicationWebClientCustomizer(filter)
  }

  @Bean
  fun fingerprintGenerator(): FingerprintGenerator {
    return DefaultFingerprintGenerator()
  }

  @Bean
  fun cacheClient(): CacheClient {
    return RedisCacheClient(redisTemplate)
  }

  @Bean
  fun cacheRuleMatcher(): CacheRuleMatcher {
    return DefaultCacheRuleMatcher(cacheProperties)
  }

  @Bean
  fun deduplicatorMetrics(): DeduplicatorMetrics {
    return DefaultDeduplicatorMetrics(meterRegistry)
  }
}