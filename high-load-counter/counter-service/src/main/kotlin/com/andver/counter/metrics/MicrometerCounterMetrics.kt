package com.andver.counter.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class MicrometerCounterMetrics(
  private val registry: MeterRegistry,
) : CounterMetrics {

  private val trackedVideos = AtomicInteger(0)
  private val shardCounters = ConcurrentHashMap<Int, Counter>()

  private val countConsumed = Counter.builder("hlc_count_events_consumed_total")
    .description("Windowed count messages consumed from Kafka")
    .register(registry)

  private val viewsBuffered = Counter.builder("hlc_views_buffered_total")
    .description("Total view deltas applied to Redis shards")
    .register(registry)

  private val uniqueConsumed = Counter.builder("hlc_unique_events_consumed_total")
    .description("Raw view events consumed for HyperLogLog")
    .register(registry)

  private val hllAdds = Counter.builder("hlc_hll_pfadd_total")
    .description("HyperLogLog PFADD operations")
    .register(registry)

  private val flushVideos = Counter.builder("hlc_flush_videos_total")
    .description("Videos flushed to Postgres")
    .register(registry)

  private val flushErrors = Counter.builder("hlc_flush_errors_total")
    .description("Flush cycle failures")
    .register(registry)

  private val dbUpsertOk = Counter.builder("hlc_db_upsert_total")
    .tag("result", "ok")
    .register(registry)

  private val dbUpsertError = Counter.builder("hlc_db_upsert_total")
    .tag("result", "error")
    .register(registry)

  private val apiGet = Counter.builder("hlc_api_get_total").register(registry)
  private val apiRecordView = Counter.builder("hlc_api_record_view_total").register(registry)

  private val redisIncrTimer = Timer.builder("hlc_redis_incr_seconds")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)

  private val redisMgetTimer = Timer.builder("hlc_redis_mget_seconds")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)

  private val hllSizeTimer = Timer.builder("hlc_hll_size_seconds")
    .publishPercentiles(0.5, 0.95)
    .register(registry)

  private val flushTimer = Timer.builder("hlc_flush_duration_seconds")
    .publishPercentiles(0.5, 0.95)
    .register(registry)

  init {
    registry.gauge("hlc_tracked_videos", trackedVideos) { it.get().toDouble() }
  }

  override fun recordCountConsumed(delta: Long) {
    countConsumed.increment()
    viewsBuffered.increment(delta.toDouble())
  }

  override fun recordUniqueConsumed() {
    uniqueConsumed.increment()
  }

  override fun recordShardIncrement(shard: Int, delta: Long) {
    shardCounters.computeIfAbsent(shard) {
      Counter.builder("hlc_redis_shard_incr_total")
        .tag("shard", shard.toString())
        .description("INCRBY ops per Redis shard — skew reveals hot-key risk")
        .register(registry)
    }.increment()
  }

  override fun recordHllAdd() {
    hllAdds.increment()
  }

  override fun recordRedisIncr(duration: Duration) {
    redisIncrTimer.record(duration)
  }

  override fun recordRedisMget(duration: Duration) {
    redisMgetTimer.record(duration)
  }

  override fun recordHllSize(duration: Duration) {
    hllSizeTimer.record(duration)
  }

  override fun recordFlush(videos: Int, duration: Duration, success: Boolean) {
    flushTimer.record(duration)
    if (success) {
      flushVideos.increment(videos.toDouble())
    } else {
      flushErrors.increment()
    }
  }

  override fun setTrackedVideos(count: Int) {
    trackedVideos.set(count)
  }

  override fun recordDbUpsert(success: Boolean) {
    if (success) dbUpsertOk.increment() else dbUpsertError.increment()
  }

  override fun recordApiGet() {
    apiGet.increment()
  }

  override fun recordApiRecordView() {
    apiRecordView.increment()
  }
}
