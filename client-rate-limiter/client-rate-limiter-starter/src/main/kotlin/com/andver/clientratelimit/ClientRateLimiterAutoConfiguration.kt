package com.andver.clientratelimit

import com.andver.clientratelimit.budget.CooldownStore
import com.andver.clientratelimit.budget.RedisCooldownStore
import com.andver.clientratelimit.budget.RedisTokenBucket
import com.andver.clientratelimit.budget.SharedBudgetGate
import com.andver.clientratelimit.budget.TokenBucket
import com.andver.clientratelimit.filter.ClientRateLimitWebClientCustomizer
import com.andver.clientratelimit.filter.WebClientClientRateLimitFilter
import com.andver.clientratelimit.metrics.ClientRateLimitMetrics
import com.andver.clientratelimit.metrics.MicrometerClientRateLimitMetrics
import com.andver.clientratelimit.metrics.NoopClientRateLimitMetrics
import com.andver.clientratelimit.rules.ClientRateLimitRuleSet
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
@ConditionalOnBean(ReactiveStringRedisTemplate::class)
@ConditionalOnProperty(prefix = "client-rate-limiter", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ClientRateLimiterProperties::class)
class ClientRateLimiterAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun clientRateLimitMetrics(meterRegistry: ObjectProvider<MeterRegistry>): ClientRateLimitMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerClientRateLimitMetrics(registry) else NoopClientRateLimitMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun clientRateLimitRuleSet(properties: ClientRateLimiterProperties): ClientRateLimitRuleSet =
    ClientRateLimitRuleSet.from(properties)

  @Bean
  @ConditionalOnMissingBean
  fun clientTokenBucket(
    redis: ReactiveStringRedisTemplate,
    properties: ClientRateLimiterProperties,
  ): TokenBucket = RedisTokenBucket(redis, properties.keyPrefix)

  @Bean
  @ConditionalOnMissingBean
  fun clientCooldownStore(
    redis: ReactiveStringRedisTemplate,
    properties: ClientRateLimiterProperties,
  ): CooldownStore = RedisCooldownStore(redis, properties.keyPrefix)

  @Bean
  @ConditionalOnMissingBean
  fun sharedBudgetGate(
    tokenBucket: TokenBucket,
    cooldown: CooldownStore,
    metrics: ClientRateLimitMetrics,
    properties: ClientRateLimiterProperties,
  ): SharedBudgetGate = SharedBudgetGate(tokenBucket, cooldown, metrics, properties.maxWait)

  @Bean
  @ConditionalOnMissingBean
  fun webClientClientRateLimitFilter(
    properties: ClientRateLimiterProperties,
    rules: ClientRateLimitRuleSet,
    budget: SharedBudgetGate,
    cooldown: CooldownStore,
    metrics: ClientRateLimitMetrics,
  ): WebClientClientRateLimitFilter =
    WebClientClientRateLimitFilter(properties, rules, budget, cooldown, metrics)

  @Bean
  @ConditionalOnMissingBean(name = ["clientRateLimitWebClientCustomizer"])
  fun clientRateLimitWebClientCustomizer(
    filter: WebClientClientRateLimitFilter,
  ): WebClientCustomizer = ClientRateLimitWebClientCustomizer(filter)
}
