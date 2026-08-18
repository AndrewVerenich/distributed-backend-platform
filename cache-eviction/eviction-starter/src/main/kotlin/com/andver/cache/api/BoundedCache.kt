package com.andver.cache.api

interface BoundedCache<K : Any, V : Any> {
  fun get(key: K): V?
  fun getOrLoad(key: K, loader: (K) -> V?): V?
  fun put(key: K, value: V)
  fun invalidate(key: K)
  fun stats(): CacheStats
}
