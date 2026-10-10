package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm

object RedisKeys {
  /**
   * Cluster-shaped key: identity is a Redis hash-tag so window suffixes derived in Lua
   * (`base:windowId`) stay in the same hash slot.
   *
   * Example: `rl:sliding-window-counter:checkout:{alice}`
   */
  fun base(prefix: String, algorithm: RateLimitAlgorithm, rule: String, identity: String): String {
    val tag = hashTag(identity)
    return "$prefix:${algorithm.configName()}:$rule:{$tag}"
  }

  fun hashTag(identity: String): String {
    val cleaned = identity.replace("{", "").replace("}", "").trim()
    return cleaned.ifBlank { "anonymous" }
  }
}
