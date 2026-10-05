package com.andver.ratelimit.model

/**
 * What the filter does when Redis is unreachable.
 * The limit itself is never decided in-process — that would split the quota across instances.
 */
enum class FailureMode {
  /** Answer 429. The quota stays honest while Redis is down. */
  REJECT,

  /** Let the request through and count it as fail-open. */
  ALLOW,
}
