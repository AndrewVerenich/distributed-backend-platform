package com.andver.cache.autoconfigure

import com.andver.cache.api.BoundedCache
import com.andver.cache.api.BoundedCacheFactory
import com.andver.cache.api.BoundedCacheRegistry
import java.util.concurrent.ConcurrentHashMap

class DefaultBoundedCacheRegistry(
  private val configBuilder: CacheConfigBuilder,
) : BoundedCacheRegistry {

  private val instances = ConcurrentHashMap<String, BoundedCache<*, *>>()

  override fun names(): Set<String> = configBuilder.names()

  @Suppress("UNCHECKED_CAST")
  override fun <K : Any, V : Any> get(
    name: String,
    seedKeys: Collection<K>,
  ): BoundedCache<K, V> {
    val existing = instances[name]
    if (existing != null) {
      return existing as BoundedCache<K, V>
    }
    val created = BoundedCacheFactory.create<K, V>(configBuilder.build(name, seedKeys))
    val raced = instances.putIfAbsent(name, created)
    return (raced ?: created) as BoundedCache<K, V>
  }
}
