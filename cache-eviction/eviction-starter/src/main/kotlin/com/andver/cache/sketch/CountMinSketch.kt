package com.andver.cache.sketch

import kotlin.math.ceil
import kotlin.math.ln

class CountMinSketch(
  expectedInsertions: Int,
  falsePositiveRate: Double,
) {
  private val width = maxOf(64, ceil(-expectedInsertions * ln(falsePositiveRate) / (ln(2.0) * ln(2.0))).toInt())
  private val depth = 4
  private val table: Array<IntArray> = Array(depth) { IntArray(width) }
  private val seeds = intArrayOf(0x9e3779b9.toInt(), 0x85ebca6b.toInt(), 0xc2b2ae35.toInt(), 0x27d4eb2f)

  fun increment(key: Any) {
    for (row in 0 until depth) {
      val col = indexFor(key, seeds[row])
      if (table[row][col] < Int.MAX_VALUE) {
        table[row][col]++
      }
    }
  }

  fun estimate(key: Any): Int {
    var min = Int.MAX_VALUE
    for (row in 0 until depth) {
      val col = indexFor(key, seeds[row])
      min = minOf(min, table[row][col])
    }
    return min
  }

  private fun indexFor(key: Any, seed: Int): Int {
    var h = key.hashCode() xor seed
    h = h xor (h ushr 16)
    return (h and Int.MAX_VALUE) % width
  }
}
