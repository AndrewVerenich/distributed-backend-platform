package com.andver.circuitbreaker.core

sealed interface AcquireResult {
  data object Rejected : AcquireResult

  class Permitted internal constructor(
    private val breaker: CountBasedCircuitBreaker,
    private val probe: Boolean,
  ) : AcquireResult {
    @Volatile
    private var settled = false

    fun recordHttpStatus(
      status: Int,
      durationNanos: Long,
    ) {
      settle { breaker.onHttpStatus(probe, status, durationNanos) }
    }

    fun recordError(durationNanos: Long) {
      settle { breaker.onError(probe, durationNanos) }
    }

    fun release() {
      settle { breaker.onRelease(probe) }
    }

    private inline fun settle(block: () -> Unit) {
      synchronized(breaker.lock) {
        if (settled) return
        settled = true
        block()
      }
    }
  }
}
