package com.andver.ratelimit.redis

import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Stable shard index from identity. Same identity → same shard for every rule/algorithm.
 */
class IdentityHashShardSelector(
  private val shardCount: Int,
) {
  init {
    require(shardCount > 0) { "shardCount must be > 0" }
  }

  fun index(identity: String): Int {
    val crc = CRC32()
    crc.update(RedisKeys.hashTag(identity).toByteArray(StandardCharsets.UTF_8))
    return (crc.value % shardCount).toInt()
  }
}
