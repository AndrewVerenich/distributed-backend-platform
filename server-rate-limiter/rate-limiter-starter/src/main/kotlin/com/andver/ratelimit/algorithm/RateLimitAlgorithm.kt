package com.andver.ratelimit.algorithm

enum class RateLimitAlgorithm {
  FIXED_WINDOW,
  SLIDING_WINDOW_LOG,
  SLIDING_WINDOW_COUNTER,
  TOKEN_BUCKET,
  ;

  /** Value used in config, Redis keys, metric tags and response headers. */
  fun configName(): String = name.lowercase().replace('_', '-')
}
