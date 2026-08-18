package com.andver.cache.demo.api

import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.demo.workload.WorkloadType

data class BenchmarkRequest(
  val policy: EvictionPolicyType = EvictionPolicyType.LRU,
  val workload: WorkloadType = WorkloadType.ZIPF,
  val requests: Int = 50_000,
  val maxSize: Int = 10_000,
  val keyspace: Int = 100_000,
  val zipfSkew: Double = 1.0,
  val ttlSeconds: Long = 120,
  val ttlJitterSeconds: Long = 30,
  val singleflightEnabled: Boolean = true,
  val negativeCachingEnabled: Boolean = true,
  val bloomFilterEnabled: Boolean = true,
) {
  fun withPolicy(newPolicy: EvictionPolicyType): BenchmarkRequest = copy(policy = newPolicy)
}
