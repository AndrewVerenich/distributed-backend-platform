package com.andver.cache.api

import java.time.Duration

data class CacheConfig<K : Any>(
  val maxSize: Int,
  val policyType: EvictionPolicyType = EvictionPolicyType.LRU,
  val ttl: Duration = Duration.ofMinutes(5),
  val ttlJitter: Duration = Duration.ZERO,
  val singleflightEnabled: Boolean = true,
  val negativeCachingEnabled: Boolean = true,
  val negativeTtl: Duration = Duration.ofSeconds(15),
  val bloomFilterEnabled: Boolean = false,
  val bloomExpectedInsertions: Int = 100_000,
  val bloomFalsePositiveRate: Double = 0.01,
  val bloomSeedKeys: Collection<K> = emptyList(),
)
