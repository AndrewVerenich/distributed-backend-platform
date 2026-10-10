package com.andver.ratelimit

import com.andver.ratelimit.filter.RateLimitWebFilter
import com.andver.ratelimit.key.RateLimitKeyResolver
import com.andver.ratelimit.metrics.MicrometerRateLimitMetrics
import com.andver.ratelimit.metrics.NoopRateLimitMetrics
import com.andver.ratelimit.metrics.RateLimitMetrics
import com.andver.ratelimit.redis.RateLimiter
import com.andver.ratelimit.redis.RedisRateLimiter
import com.andver.ratelimit.redis.RedisShardRegistry
import com.andver.ratelimit.rules.RateLimitRuleSet
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
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
 * After Redis reactive auto-config for the single-node path.
 * Sharded mode builds its own Lettuce connections from [RateLimiterProperties.sharding].
 */
@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
@ConditionalOnProperty(prefix = "rate-limiter", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimiterProperties::class)
class RateLimiterAutoConfiguration {

  private val log = LoggerFactory.getLogger(RateLimiterAutoConfiguration::class.java)

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

  @Bean(destroyMethod = "destroy")
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
    prefix = "rate-limiter.sharding",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
  )
  @ConditionalOnBean(ReactiveStringRedisTemplate::class)
  fun singleRedisShardRegistry(
    redis: ReactiveStringRedisTemplate,
    properties: RateLimiterProperties,
  ): RedisShardRegistry {
    val name = properties.sharding.singleShardName.ifBlank { "default" }
    log.info("rate-limiter sharding disabled; using single shard name={}", name)
    return RedisShardRegistry.single(name, redis)
  }

  @Bean(destroyMethod = "destroy")
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "rate-limiter.sharding", name = ["enabled"], havingValue = "true")
  fun shardedRedisShardRegistry(properties: RateLimiterProperties): RedisShardRegistry {
    val registry = RedisShardRegistry.fromNodes(properties.sharding.nodes)
    log.info(
      "rate-limiter sharding enabled shards={}",
      registry.all().joinToString { "${it.index}:${it.name}" },
    )
    return registry
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RedisShardRegistry::class)
  fun rateLimiter(
    shards: RedisShardRegistry,
    properties: RateLimiterProperties,
    metrics: RateLimitMetrics,
  ): RateLimiter = RedisRateLimiter(shards, properties.keyPrefix, metrics)

  @Bean
  @ConditionalOnBean(RateLimiter::class)
  @ConditionalOnProperty(prefix = "rate-limiter.filter", name = ["enabled"], havingValue = "true", matchIfMissing = true)
  fun rateLimitWebFilter(
    limiter: RateLimiter,
    rules: RateLimitRuleSet,
    keys: RateLimitKeyResolver,
    metrics: RateLimitMetrics,
    properties: RateLimiterProperties,
  ): WebFilter = RateLimitWebFilter(limiter, rules, keys, metrics, properties)
}
