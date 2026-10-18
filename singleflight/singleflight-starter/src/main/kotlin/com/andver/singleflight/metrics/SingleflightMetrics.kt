package com.andver.singleflight.metrics

interface SingleflightMetrics {
  fun leaderStart(mode: String)
  fun waiterJoin(mode: String)
  fun upstreamCall(mode: String)
  fun waiterTimeout(mode: String)
  fun leaderFailover(mode: String)
  fun sharedError(mode: String)
}

object NoopSingleflightMetrics : SingleflightMetrics {
  override fun leaderStart(mode: String) = Unit
  override fun waiterJoin(mode: String) = Unit
  override fun upstreamCall(mode: String) = Unit
  override fun waiterTimeout(mode: String) = Unit
  override fun leaderFailover(mode: String) = Unit
  override fun sharedError(mode: String) = Unit
}
