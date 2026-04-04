package com.andver.sharding.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sharding")
data class ShardingProperties(
  val defaultShard: String? = null,
  val shards: List<ShardProperties> = emptyList(),
)

data class ShardProperties(
  val name: String,
  val host: String,
  val port: Int = 5432,
  val database: String,
  val username: String,
  val password: String,
)

