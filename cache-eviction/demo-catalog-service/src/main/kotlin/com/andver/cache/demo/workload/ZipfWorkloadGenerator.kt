package com.andver.cache.demo.workload

import kotlin.random.Random

class ZipfWorkloadGenerator(
  keyspace: Int,
  skew: Double = 1.0,
) : WorkloadGenerator {
  private val cumulative = DoubleArray(keyspace)
  private val random = Random.Default

  init {
    var norm = 0.0
    for (i in 1..keyspace) {
      norm += 1.0 / Math.pow(i.toDouble(), skew)
    }

    var acc = 0.0
    for (i in 1..keyspace) {
      acc += (1.0 / Math.pow(i.toDouble(), skew)) / norm
      cumulative[i - 1] = acc
    }
  }

  override fun nextKey(): String {
    val p = random.nextDouble()
    var lo = 0
    var hi = cumulative.lastIndex
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (cumulative[mid] < p) lo = mid + 1 else hi = mid
    }
    return "sku-$lo"
  }
}
