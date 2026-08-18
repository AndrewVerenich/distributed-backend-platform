package com.andver.cache.api


interface BoundedCacheRegistry {
  fun names(): Set<String>

  fun <K : Any, V : Any> get(name: String, seedKeys: Collection<K> = emptyList()): BoundedCache<K, V>
}
