package com.andver.cache.api

import com.andver.cache.runtime.InMemoryBoundedCache
import java.time.Clock

object BoundedCacheFactory {
  fun <K : Any, V : Any> create(config: CacheConfig<K>, clock: Clock = Clock.systemUTC()): BoundedCache<K, V> =
    InMemoryBoundedCache(config = config, clock = clock)
}
