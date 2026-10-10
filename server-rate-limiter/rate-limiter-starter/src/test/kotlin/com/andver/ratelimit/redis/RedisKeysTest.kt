package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedisKeysTest {

  @Test
  fun `wraps identity in a redis hash-tag`() {
    assertEquals(
      "rl:sliding-window-counter:checkout:{alice}",
      RedisKeys.base("rl", RateLimitAlgorithm.SLIDING_WINDOW_COUNTER, "checkout", "alice"),
    )
  }

  @Test
  fun `strips braces from identity before tagging`() {
    assertEquals("user1", RedisKeys.hashTag("user}1{"))
    assertEquals(
      "rl:fixed-window:catalog:{user1}",
      RedisKeys.base("rl", RateLimitAlgorithm.FIXED_WINDOW, "catalog", "{user1}"),
    )
  }

  @Test
  fun `blank identity becomes anonymous tag`() {
    assertEquals("anonymous", RedisKeys.hashTag("   "))
    assertEquals(
      "rl:token-bucket:catalog:{anonymous}",
      RedisKeys.base("rl", RateLimitAlgorithm.TOKEN_BUCKET, "catalog", ""),
    )
  }
}

class IdentityHashShardSelectorTest {

  @Test
  fun `same identity is sticky`() {
    val selector = IdentityHashShardSelector(3)
    val first = selector.index("alice")
    repeat(20) {
      assertEquals(first, selector.index("alice"))
    }
  }

  @Test
  fun `indexes stay inside shard range`() {
    val selector = IdentityHashShardSelector(4)
    val indexes = (0 until 200).map { selector.index("user-$it") }.toSet()
    assertTrue(indexes.all { it in 0..3 })
    assertTrue(indexes.size > 1)
  }
}
