package com.andver.cache.demo.api

import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.demo.workload.WorkloadType

data class BenchmarkComparisonRequest(
  val workload: WorkloadType = WorkloadType.ZIPF,
  val policies: List<EvictionPolicyType> = EvictionPolicyType.entries,
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
  fun toBenchmarkRequest(policy: EvictionPolicyType): BenchmarkRequest = BenchmarkRequest(
    policy = policy,
    workload = workload,
    requests = requests,
    maxSize = maxSize,
    keyspace = keyspace,
    zipfSkew = zipfSkew,
    ttlSeconds = ttlSeconds,
    ttlJitterSeconds = ttlJitterSeconds,
    singleflightEnabled = singleflightEnabled,
    negativeCachingEnabled = negativeCachingEnabled,
    bloomFilterEnabled = bloomFilterEnabled,
  )
}
