package com.andver.sharding.resolver

import com.andver.sharding.hash.ShardHashFunction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShardResolverTest {

  @Test
  fun `returns default shard when shard list is empty`() {
    val resolver = ShardResolver(
      shardNames = emptyList(),
      defaultShard = "default",
      hashFunction = object : ShardHashFunction {
        override fun hash(value: String): Long = 123L
      },
    )

    assertEquals("default", resolver.resolveShardForKey("key-1"))
  }

  @Test
  fun `resolves shard by hash modulo shard count`() {
    val shards = listOf("s0", "s1", "s2", "s3")

    val resolver = ShardResolver(
      shardNames = shards,
      defaultShard = "default",
      hashFunction = object : ShardHashFunction {
        override fun hash(value: String): Long = value.toLong()
      },
    )

    assertEquals("s0", resolver.resolveShardForKey("0"))
    assertEquals("s1", resolver.resolveShardForKey("1"))
    assertEquals("s3", resolver.resolveShardForKey("3"))
    // 4 % 4 = 0
    assertEquals("s0", resolver.resolveShardForKey("4"))
  }
}

