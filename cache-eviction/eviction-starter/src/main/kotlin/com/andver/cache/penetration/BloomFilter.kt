package com.andver.cache.penetration

import java.util.BitSet
import kotlin.math.ceil
import kotlin.math.ln

class BloomFilter(
  expectedInsertions: Int,
  falsePositiveRate: Double,
) {
  private val bitCount = maxOf(1024, ceil(-expectedInsertions * ln(falsePositiveRate) / (ln(2.0) * ln(2.0))).toInt())
  private val hashFunctions = maxOf(2, ceil((bitCount.toDouble() / expectedInsertions) * ln(2.0)).toInt())
  private val bits = BitSet(bitCount)

  fun put(value: Any) {
    for (i in 0 until hashFunctions) {
      bits.set(index(value, i))
    }
  }

  fun mightContain(value: Any): Boolean {
    for (i in 0 until hashFunctions) {
      if (!bits.get(index(value, i))) {
        return false
      }
    }
    return true
  }

  private fun index(value: Any, seed: Int): Int {
    var h = value.hashCode() * 31 + seed * 0x9e3779b9.toInt()
    h = h xor (h ushr 16)
    return (h and Int.MAX_VALUE) % bitCount
  }
}
