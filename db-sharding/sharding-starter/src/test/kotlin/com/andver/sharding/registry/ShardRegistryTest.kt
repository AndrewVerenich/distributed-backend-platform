package com.andver.sharding.registry

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.r2dbc.spi.ConnectionFactory

class ShardRegistryTest {

  @Test
  fun `exposes known shard names and default shard`() {
    val shardNames = listOf("a", "b")
    val defaultShard = "a"

    val f1 = mockk<ConnectionFactory>()
    val f2 = mockk<ConnectionFactory>()

    val registry = ShardRegistry(
      shardNames = shardNames,
      defaultShard = defaultShard,
      connectionFactories = mapOf("a" to f1, "b" to f2),
    )

    assertEquals(shardNames, registry.allShardNames())
    assertEquals(defaultShard, registry.defaultShardName())
    assertEquals(f2, registry.connectionFactory("b"))
  }

  @Test
  fun `throws on unknown shard name`() {
    val defaultShard = "a"
    val registry = ShardRegistry(
      shardNames = listOf("a"),
      defaultShard = defaultShard,
      connectionFactories = emptyMap(),
    )

    assertThrows(IllegalStateException::class.java) {
      registry.connectionFactory("unknown")
    }
  }
}

