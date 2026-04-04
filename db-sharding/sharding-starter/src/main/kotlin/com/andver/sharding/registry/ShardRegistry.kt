package com.andver.sharding.registry

import io.r2dbc.spi.ConnectionFactory

class ShardRegistry(
  private val shardNames: List<String>,
  private val defaultShard: String,
  private val connectionFactories: Map<String, ConnectionFactory>,
) {
  fun allShardNames(): List<String> = shardNames

  fun defaultShardName(): String = defaultShard

  fun connectionFactory(shardName: String): ConnectionFactory {
    return connectionFactories[shardName]
      ?: error("Unknown shardName=$shardName (known=${shardNames.joinToString()})")
  }
}