package com.andver.sharding.user.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class ShardingMetrics(
  private val registry: MeterRegistry,
) {
  private val ops = ConcurrentHashMap<String, Counter>()
  private val rowGauges = ConcurrentHashMap<String, AtomicLong>()
  private val scatterTimer = Timer.builder("dbs_scatter_gather_seconds")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)

  fun recordOp(shard: String, op: String) {
    ops.computeIfAbsent("$shard/$op") {
      Counter.builder("dbs_ops_total")
        .tag("shard", shard)
        .tag("op", op)
        .register(registry)
    }.increment()
  }

  fun recordScatterGather(duration: Duration) {
    scatterTimer.record(duration)
  }

  fun setRows(shard: String, count: Long) {
    rowGauges.computeIfAbsent(shard) { key ->
      val atomic = AtomicLong(0)
      Gauge.builder("dbs_rows_per_shard", atomic) { it.get().toDouble() }
        .tag("shard", key)
        .register(registry)
      atomic
    }.set(count)
  }
}
