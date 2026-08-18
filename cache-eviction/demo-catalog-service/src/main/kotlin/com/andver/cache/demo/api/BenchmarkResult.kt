package com.andver.cache.demo.api

import com.andver.cache.api.CacheStats
import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.demo.workload.WorkloadType

data class BenchmarkResult(
  val policy: EvictionPolicyType,
  val workload: WorkloadType,
  val requests: Int,
  val elapsedMs: Long,
  val p50LatencyMicros: Long,
  val p99LatencyMicros: Long,
  val stats: CacheStats,
)
