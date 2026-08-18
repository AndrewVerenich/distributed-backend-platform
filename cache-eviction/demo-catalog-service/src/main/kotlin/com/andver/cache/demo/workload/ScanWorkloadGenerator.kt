package com.andver.cache.demo.workload

class ScanWorkloadGenerator(
  private val keyspace: Int,
) : WorkloadGenerator {
  private var idx = 0

  override fun nextKey(): String {
    val key = "sku-${idx % keyspace}"
    idx++
    return key
  }
}
