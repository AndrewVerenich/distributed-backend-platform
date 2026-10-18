package com.andver.singleflight

import com.andver.singleflight.metrics.SingleflightMetrics
import reactor.core.publisher.Mono

/** No coalescing: every call runs the loader (baseline for demos). */
class PassthroughSingleflight(
  private val metrics: SingleflightMetrics,
) : Singleflight {

  override fun <T : Any> execute(
    key: String,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> {
    metrics.leaderStart(MODE)
    metrics.upstreamCall(MODE)
    return loader().map { SingleflightOutcome(it, SingleflightRole.LEADER) }
  }

  companion object {
    private const val MODE = "off"
  }
}
