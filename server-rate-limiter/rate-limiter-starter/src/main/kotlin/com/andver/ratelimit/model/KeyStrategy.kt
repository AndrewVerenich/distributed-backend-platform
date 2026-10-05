package com.andver.ratelimit.model

enum class KeyStrategy {
  /** One bucket per value of [com.andver.ratelimit.RateLimiterProperties.KeyProperties.header]. */
  HEADER,

  /** One bucket per remote address. */
  IP,
}
