package com.andver.cache.api

data class CacheStats(
  val hits: Long,
  val misses: Long,
  val evictions: Long,
  val loadCount: Long,
  val negativeHits: Long,
  val bloomRejects: Long,
  val size: Int,
) {
  val hitRatio: Double
    get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses).toDouble()
}
