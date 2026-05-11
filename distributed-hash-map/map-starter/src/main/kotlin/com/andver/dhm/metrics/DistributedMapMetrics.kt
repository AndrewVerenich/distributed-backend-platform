package com.andver.dhm.metrics

import com.andver.dhm.envelope.Operation
import com.andver.dhm.runtime.MapRuntimeRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Micrometer surface for the distributed hash map. All counters/gauges are tagged with the
 * map name so a multi-map deployment renders cleanly in Grafana.
 */
class DistributedMapMetrics(
  private val registry: MeterRegistry,
) {

  private val publishCounters = ConcurrentHashMap<String, Counter>()
  private val appliedCounters = ConcurrentHashMap<String, Counter>()
  private val rejectedCounters = ConcurrentHashMap<String, Counter>()
  private val cleanupCounters = ConcurrentHashMap<String, Counter>()
  private val bootstrapTimers = ConcurrentHashMap<String, Timer>()

  fun bindGauges(runtime: MapRuntimeRegistry) {
    runtime.all().forEach { (name, map) ->
      registry.gauge(
        "distributed_map.size",
        listOf(Tag.of("map", name)),
        map,
      ) { it.localState.liveSize().toDouble() }
      registry.gauge(
        "distributed_map.tombstones",
        listOf(Tag.of("map", name)),
        map,
      ) { it.localState.tombstoneSize().toDouble() }
      registry.gauge(
        "distributed_map.applied_total_state",
        listOf(Tag.of("map", name)),
        map,
      ) { it.localState.appliedCount().toDouble() }
    }
  }

  fun recordPublish(mapName: String, operation: Operation) {
    publishCounters.computeIfAbsent("$mapName/${operation.name}") {
      Counter.builder("distributed_map.publish.total")
        .tag("map", mapName)
        .tag("operation", operation.name.lowercase())
        .register(registry)
    }.increment()
  }

  fun recordEventApplied(mapName: String, changed: Boolean) {
    if (changed) {
      appliedCounters.computeIfAbsent(mapName) {
        Counter.builder("distributed_map.applied.total")
          .tag("map", mapName)
          .register(registry)
      }.increment()
    } else {
      rejectedCounters.computeIfAbsent(mapName) {
        Counter.builder("distributed_map.rejected.total")
          .tag("map", mapName)
          .register(registry)
      }.increment()
    }
  }

  fun recordTombstoneEvictions(mapName: String, count: Int) {
    if (count == 0) return
    cleanupCounters.computeIfAbsent(mapName) {
      Counter.builder("distributed_map.tombstones.evicted.total")
        .tag("map", mapName)
        .register(registry)
    }.increment(count.toDouble())
  }

  fun recordBootstrapDuration(mapName: String, duration: Duration) {
    bootstrapTimers.computeIfAbsent(mapName) {
      Timer.builder("distributed_map.bootstrap.duration.seconds")
        .tag("map", mapName)
        .publishPercentiles(0.5, 0.95)
        .register(registry)
    }.record(duration)
  }
}
