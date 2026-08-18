package com.andver.cache.autoconfigure

import com.andver.cache.api.EvictionPolicyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class NamedCacheConfigTest {

  private val properties = CacheEvictionProperties(
    maxSize = 10_000,
    policy = EvictionPolicyType.LRU,
    ttl = Duration.ofMinutes(5),
    ttlJitter = Duration.ofSeconds(30),
    caches = mapOf(
      "products" to CacheInstanceProperties(
        policy = EvictionPolicyType.W_TINY_LFU,
        maxSize = 50_000,
      ),
      "sessions" to CacheInstanceProperties(
        ttl = Duration.ofMinutes(10),
        maxSize = 2_000,
      ),
    ),
  )

  private val builder = CacheConfigBuilder(properties)
  private val registry = DefaultBoundedCacheRegistry(builder)

  @Test
  fun `named cache overlays only specified fields`() {
    val products = builder.build<String>("products")
    assertEquals(50_000, products.maxSize)
    assertEquals(EvictionPolicyType.W_TINY_LFU, products.policyType)
    assertEquals(Duration.ofMinutes(5), products.ttl)

    val sessions = builder.build<String>("sessions")
    assertEquals(2_000, sessions.maxSize)
    assertEquals(EvictionPolicyType.LRU, sessions.policyType)
    assertEquals(Duration.ofMinutes(10), sessions.ttl)
  }

  @Test
  fun `unknown name falls back to defaults`() {
    val adHoc = builder.build<String>("unknown")
    assertEquals(10_000, adHoc.maxSize)
    assertEquals(EvictionPolicyType.LRU, adHoc.policyType)
  }

  @Test
  fun `registry reuses instance per name`() {
    val a = registry.get<String, String>("products")
    val b = registry.get<String, String>("products")
    val c = registry.get<String, String>("sessions")
    assertSame(a, b)
    assertTrue(a !== c)
    assertEquals(setOf("products", "sessions"), registry.names())
  }
}
