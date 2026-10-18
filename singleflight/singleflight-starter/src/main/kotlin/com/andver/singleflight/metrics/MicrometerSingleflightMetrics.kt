package com.andver.singleflight.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class MicrometerSingleflightMetrics(
  private val registry: MeterRegistry,
) : SingleflightMetrics {

  override fun leaderStart(mode: String) = counter("sf_leader_starts_total", mode).increment()

  override fun waiterJoin(mode: String) = counter("sf_waiter_joins_total", mode).increment()

  override fun upstreamCall(mode: String) = counter("sf_upstream_calls_total", mode).increment()

  override fun waiterTimeout(mode: String) = counter("sf_waiter_timeouts_total", mode).increment()

  override fun leaderFailover(mode: String) = counter("sf_leader_failover_total", mode).increment()

  override fun sharedError(mode: String) = counter("sf_shared_errors_total", mode).increment()

  private fun counter(name: String, mode: String): Counter =
    Counter.builder(name)
      .tag("mode", mode)
      .register(registry)
}
