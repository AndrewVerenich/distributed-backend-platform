package com.andver.cache.autoconfigure

import com.andver.cache.api.CacheConfig

class CacheConfigBuilder(
  private val properties: CacheEvictionProperties,
) {
  fun names(): Set<String> = properties.caches.keys

  /** Defaults only (no named override). */
  fun <K : Any> build(seedKeys: Collection<K> = emptyList()): CacheConfig<K> =
    merge(override = null, seedKeys = seedKeys)

  /**
   * Named cache: fields from `cache.eviction.caches.<name>` overlay top-level defaults.
   * Unknown [name] falls back to defaults so ad-hoc caches still work.
   */
  fun <K : Any> build(name: String, seedKeys: Collection<K> = emptyList()): CacheConfig<K> =
    merge(override = properties.caches[name], seedKeys = seedKeys)

  private fun <K : Any> merge(
    override: CacheInstanceProperties?,
    seedKeys: Collection<K>,
  ): CacheConfig<K> = CacheConfig(
    maxSize = override?.maxSize ?: properties.maxSize,
    policyType = override?.policy ?: properties.policy,
    ttl = override?.ttl ?: properties.ttl,
    ttlJitter = override?.ttlJitter ?: properties.ttlJitter,
    singleflightEnabled = override?.singleflightEnabled ?: properties.singleflightEnabled,
    negativeCachingEnabled = override?.negativeCachingEnabled ?: properties.negativeCachingEnabled,
    negativeTtl = override?.negativeTtl ?: properties.negativeTtl,
    bloomFilterEnabled = override?.bloomFilterEnabled ?: properties.bloomFilterEnabled,
    bloomExpectedInsertions = override?.bloomExpectedInsertions ?: properties.bloomExpectedInsertions,
    bloomFalsePositiveRate = override?.bloomFalsePositiveRate ?: properties.bloomFalsePositiveRate,
    bloomSeedKeys = seedKeys,
  )
}
