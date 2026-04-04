package com.andver.sharding.resolver

import com.andver.sharding.hash.MurmurHash3Function
import com.andver.sharding.hash.ShardHashFunction

class ShardResolver(
  private val shardNames: List<String>,
  private val defaultShard: String,
  private val hashFunction: ShardHashFunction = MurmurHash3Function(),
) {
  fun resolveShardForKey(key: String): String {
    if (shardNames.isEmpty()) return defaultShard
    val idx = (hashFunction.hash(key) % shardNames.size.toLong()).toInt()
    return shardNames[idx]
  }
}