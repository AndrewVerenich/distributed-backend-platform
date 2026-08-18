package com.andver.cache.demo.workload

class LoopingWorkloadGenerator(
  private val workingSet: Int,
) : WorkloadGenerator {
  private var idx = 0

  override fun nextKey(): String {
    val key = "sku-${idx % workingSet}"
    idx++
    return key
  }
}
