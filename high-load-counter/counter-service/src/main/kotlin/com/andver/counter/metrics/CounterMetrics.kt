package com.andver.counter.metrics

import java.time.Duration

/**
 * Domain SLIs for the high-load counter pipeline:
 * Kafka consume → Redis shard INCR / HLL → Postgres flush.
 */
interface CounterMetrics {
  fun recordCountConsumed(delta: Long)
  fun recordUniqueConsumed()
  fun recordShardIncrement(shard: Int, delta: Long)
  fun recordHllAdd()
  fun recordRedisIncr(duration: Duration)
  fun recordRedisMget(duration: Duration)
  fun recordHllSize(duration: Duration)
  fun recordFlush(videos: Int, duration: Duration, success: Boolean)
  fun setTrackedVideos(count: Int)
  fun recordDbUpsert(success: Boolean)
  fun recordApiGet()
  fun recordApiRecordView()
}
