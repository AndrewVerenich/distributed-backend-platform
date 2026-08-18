package com.andver.cache.demo.api

import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.demo.workload.WorkloadType

data class BenchmarkComparisonResult(
  val workload: WorkloadType,
  val requests: Int,
  val results: List<BenchmarkResult>,
  val bestByHitRatio: EvictionPolicyType?,
  val bestByP99Latency: EvictionPolicyType?,
)
