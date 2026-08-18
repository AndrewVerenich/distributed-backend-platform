package com.andver.cache.runtime

import com.andver.cache.api.BoundedCacheFactory
import com.andver.cache.api.CacheConfig
import com.andver.cache.api.EvictionPolicyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InMemoryBoundedCacheTest {
  @Test
  fun `lru evicts least recently used key`() {
    val cache = BoundedCacheFactory.create<String, String>(
      CacheConfig(maxSize = 2, policyType = EvictionPolicyType.LRU, ttl = Duration.ofMinutes(1)),
    )

    cache.put("a", "A")
    cache.put("b", "B")
    cache.get("a")
    cache.put("c", "C")

    assertEquals("A", cache.get("a"))
    assertNull(cache.get("b"))
    assertEquals("C", cache.get("c"))
  }

  @Test
  fun `singleflight loads key exactly once under contention`() {
    val cache = BoundedCacheFactory.create<String, String>(
      CacheConfig(
        maxSize = 10,
        policyType = EvictionPolicyType.LRU,
        ttl = Duration.ofMinutes(1),
        singleflightEnabled = true,
      ),
    )
    val loads = AtomicInteger(0)
    val pool = Executors.newFixedThreadPool(16)

    val tasks = (1..100).map {
      Callable {
        cache.getOrLoad("hot-key") {
          loads.incrementAndGet()
          Thread.sleep(25)
          "value"
        }
      }
    }

    val futures = pool.invokeAll(tasks)
    futures.forEach { assertEquals("value", it.get(2, TimeUnit.SECONDS)) }
    pool.shutdownNow()

    assertEquals(1, loads.get())
    assertEquals(1, cache.stats().loadCount)
  }

  @Test
  fun `negative caching avoids repeated loads for absent key`() {
    val cache = BoundedCacheFactory.create<String, String>(
      CacheConfig(
        maxSize = 10,
        policyType = EvictionPolicyType.FIFO,
        ttl = Duration.ofMinutes(1),
        negativeCachingEnabled = true,
        negativeTtl = Duration.ofSeconds(30),
      ),
    )
    val loads = AtomicInteger(0)

    repeat(20) {
      cache.getOrLoad("missing") {
        loads.incrementAndGet()
        null
      }
    }

    assertEquals(1, loads.get())
  }
}
