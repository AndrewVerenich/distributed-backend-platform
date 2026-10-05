package com.andver.ratelimit

import com.andver.ratelimit.filter.RateLimitWebFilter
import com.andver.ratelimit.key.RateLimitKeyResolver
import com.andver.ratelimit.metrics.MicrometerRateLimitMetrics
import com.andver.ratelimit.metrics.NoopRateLimitMetrics
import com.andver.ratelimit.metrics.RateLimitMetrics
import com.andver.ratelimit.redis.RateLimiter
import com.andver.ratelimit.redis.RedisRateLimiter
import com.andver.ratelimit.rules.RateLimitRuleSet
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.web.server.WebFilter

/**
 * After Redis reactive auto-config: a class-level [ConditionalOnBean] on
 * [ReactiveStringRedisTemplate] is evaluated too early and skips every bean.
 */
@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
@ConditionalOnBean(ReactiveStringRedisTemplate::class)
@ConditionalOnProperty(prefix = "rate-limiter", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimiterProperties::class)
class RateLimiterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun rateLimitMetrics(meterRegistry: ObjectProvider<MeterRegistry>): RateLimitMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerRateLimitMetrics(registry) else NoopRateLimitMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun rateLimitRuleSet(properties: RateLimiterProperties): RateLimitRuleSet =
    RateLimitRuleSet.from(properties)

  @Bean
  @ConditionalOnMissingBean
  fun rateLimitKeyResolver(properties: RateLimiterProperties): RateLimitKeyResolver =
    RateLimitKeyResolver(properties.key.fallbackToIp)

  @Bean
  @ConditionalOnMissingBean
  fun rateLimiter(
    redis: ReactiveStringRedisTemplate,
    properties: RateLimiterProperties,
    metrics: RateLimitMetrics,
  ): RateLimiter = RedisRateLimiter(redis, properties.keyPrefix, metrics)

  @Bean
  @ConditionalOnProperty(prefix = "rate-limiter.filter", name = ["enabled"], havingValue = "true", matchIfMissing = true)
  fun rateLimitWebFilter(
    limiter: RateLimiter,
    rules: RateLimitRuleSet,
    keys: RateLimitKeyResolver,
    metrics: RateLimitMetrics,
    properties: RateLimiterProperties,
  ): WebFilter = RateLimitWebFilter(limiter, rules, keys, metrics, properties)
}
