package com.andver.cache.runtime

import com.andver.cache.api.BoundedCache
import com.andver.cache.api.CacheConfig
import com.andver.cache.api.CacheStats
import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.penetration.BloomFilter
import com.andver.cache.policy.ClockPolicy
import com.andver.cache.policy.EvictionPolicy
import com.andver.cache.policy.FifoPolicy
import com.andver.cache.policy.LfuPolicy
import com.andver.cache.policy.LruPolicy
import com.andver.cache.policy.WTinyLfuPolicy
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemoryBoundedCache<K : Any, V : Any>(
  private val config: CacheConfig<K>,
  private val clock: Clock = Clock.systemUTC(),
) : BoundedCache<K, V> {
  private val store = ConcurrentHashMap<K, CacheRecord<V>>()
  private val lock = ReentrantLock()
  private val inflightLoads = ConcurrentHashMap<K, CompletableFuture<V?>>()
  private val policy: EvictionPolicy<K> = createPolicy(config)
  private val tinyLfuPolicy = policy as? WTinyLfuPolicy<K>
  private val bloom: BloomFilter? = if (config.bloomFilterEnabled) {
    BloomFilter(config.bloomExpectedInsertions, config.bloomFalsePositiveRate)
  } else {
    null
  }

  private val hits = AtomicLong(0)
  private val misses = AtomicLong(0)
  private val evictions = AtomicLong(0)
  private val loads = AtomicLong(0)
  private val negativeHits = AtomicLong(0)
  private val bloomRejects = AtomicLong(0)

  init {
    config.bloomSeedKeys.forEach { bloom?.put(it) }
  }

  override fun get(key: K): V? {
    val now = now()
    lock.withLock {
      val rec = store[key] ?: run {
        misses.incrementAndGet()
        return null
      }
      if (rec.expiresAtMillis <= now) {
        removeInternal(key)
        misses.incrementAndGet()
        return null
      }
      policy.onGet(key)
      hits.incrementAndGet()
      if (rec.negative) {
        negativeHits.incrementAndGet()
        return null
      }
      return rec.value
    }
  }

  override fun getOrLoad(key: K, loader: (K) -> V?): V? {
    val fromCache = get(key)
    if (fromCache != null || containsNegative(key)) {
      return fromCache
    }

    if (bloom != null && !bloom.mightContain(key)) {
      bloomRejects.incrementAndGet()
      return null
    }

    if (!config.singleflightEnabled) {
      return loadAndStore(key, loader)
    }

    val existing = inflightLoads[key]
    if (existing != null) {
      return existing.join()
    }

    val created = CompletableFuture<V?>()
    val raced = inflightLoads.putIfAbsent(key, created)
    if (raced != null) {
      return raced.join()
    }

    try {
      val value = loadAndStore(key, loader)
      created.complete(value)
      return value
    } catch (e: Throwable) {
      created.completeExceptionally(e)
      throw e
    } finally {
      inflightLoads.remove(key, created)
    }
  }

  override fun put(key: K, value: V) {
    lock.withLock {
      putInternal(key = key, value = value, negative = false, ttlMillis = ttlWithJitterMillis(config.ttl.toMillis()))
      bloom?.put(key)
    }
  }

  override fun invalidate(key: K) {
    lock.withLock {
      removeInternal(key)
    }
  }

  override fun stats(): CacheStats = CacheStats(
    hits = hits.get(),
    misses = misses.get(),
    evictions = evictions.get(),
    loadCount = loads.get(),
    negativeHits = negativeHits.get(),
    bloomRejects = bloomRejects.get(),
    size = store.size,
  )

  private fun loadAndStore(key: K, loader: (K) -> V?): V? {
    loads.incrementAndGet()
    val loaded = loader(key)
    lock.withLock {
      if (loaded == null) {
        if (config.negativeCachingEnabled) {
          putInternal(key, null, negative = true, ttlMillis = ttlWithJitterMillis(config.negativeTtl.toMillis()))
        }
        return null
      }
      putInternal(key, loaded, negative = false, ttlMillis = ttlWithJitterMillis(config.ttl.toMillis()))
      bloom?.put(key)
      return loaded
    }
  }

  private fun putInternal(key: K, value: V?, negative: Boolean, ttlMillis: Long) {
    val isNew = !store.containsKey(key)
    store[key] = CacheRecord(
      value = value,
      expiresAtMillis = now() + ttlMillis,
      negative = negative,
    )
    policy.onPut(key, isNew)
    evictIfNeeded(lastInsertedKey = key)
  }

  private fun evictIfNeeded(lastInsertedKey: K) {
    while (store.size > config.maxSize) {
      val victim = policy.pickVictim() ?: return
      if (victim == lastInsertedKey && tinyLfuPolicy != null) {
        val fallbackVictim = store.keys.firstOrNull { it != lastInsertedKey }
        if (!tinyLfuPolicy.admitted(lastInsertedKey, fallbackVictim)) {
          removeInternal(lastInsertedKey)
          evictions.incrementAndGet()
          return
        }
      }
      if (removeInternal(victim)) {
        evictions.incrementAndGet()
      }
    }
  }

  private fun removeInternal(key: K): Boolean {
    val removed = store.remove(key) != null
    if (removed) {
      policy.onRemove(key)
    }
    return removed
  }

  private fun containsNegative(key: K): Boolean = lock.withLock {
    val rec = store[key] ?: return false
    if (rec.expiresAtMillis <= now()) {
      removeInternal(key)
      return false
    }
    return rec.negative
  }

  private fun ttlWithJitterMillis(base: Long): Long {
    val jitter = config.ttlJitter.toMillis()
    if (jitter <= 0) return base
    return base + ThreadLocalRandom.current().nextLong(0, jitter + 1)
  }

  private fun now(): Long = clock.millis()

  private data class CacheRecord<V>(
    val value: V?,
    val expiresAtMillis: Long,
    val negative: Boolean,
  )

  companion object {
    private fun <K : Any> createPolicy(config: CacheConfig<K>): EvictionPolicy<K> = when (config.policyType) {
      EvictionPolicyType.FIFO -> FifoPolicy()
      EvictionPolicyType.LRU -> LruPolicy()
      EvictionPolicyType.LFU -> LfuPolicy()
      EvictionPolicyType.CLOCK -> ClockPolicy()
      EvictionPolicyType.W_TINY_LFU -> WTinyLfuPolicy(config.bloomExpectedInsertions)
    }
  }
}
