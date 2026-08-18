package com.andver.cache.autoconfigure

import com.andver.cache.api.EvictionPolicyType
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "cache.eviction")
data class CacheEvictionProperties(
  val maxSize: Int = 10_000,
  val policy: EvictionPolicyType = EvictionPolicyType.LRU,
  val ttl: Duration = Duration.ofMinutes(5),
  val ttlJitter: Duration = Duration.ofSeconds(30),
  val singleflightEnabled: Boolean = true,
  val negativeCachingEnabled: Boolean = true,
  val negativeTtl: Duration = Duration.ofSeconds(15),
  val bloomFilterEnabled: Boolean = true,
  val bloomExpectedInsertions: Int = 100_000,
  val bloomFalsePositiveRate: Double = 0.01,
  val caches: Map<String, CacheInstanceProperties> = emptyMap(),
)

data class CacheInstanceProperties(
  val maxSize: Int? = null,
  val policy: EvictionPolicyType? = null,
  val ttl: Duration? = null,
  val ttlJitter: Duration? = null,
  val singleflightEnabled: Boolean? = null,
  val negativeCachingEnabled: Boolean? = null,
  val negativeTtl: Duration? = null,
  val bloomFilterEnabled: Boolean? = null,
  val bloomExpectedInsertions: Int? = null,
  val bloomFalsePositiveRate: Double? = null,
)
