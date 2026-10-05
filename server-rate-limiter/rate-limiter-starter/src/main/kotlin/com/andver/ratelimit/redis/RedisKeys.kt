package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm

object RedisKeys {
  fun base(prefix: String, algorithm: RateLimitAlgorithm, rule: String, identity: String): String =
    "$prefix:${algorithm.configName()}:$rule:$identity"
}
