package com.andver.dhm.cleanup

import com.andver.dhm.kafka.MapEventProducer
import com.andver.dhm.metrics.DistributedMapMetrics
import com.andver.dhm.properties.DistributedMapProperties
import com.andver.dhm.runtime.MapRuntimeRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodically evicts old tombstones from local state. Optionally also publishes Kafka
 * compaction-tombstones for the same keys so the broker can free disk space upstream.
 *
 * The job runs on a single daemon thread and never blocks the consumer loop.
 */
class TombstoneCleaner(
  private val properties: DistributedMapProperties,
  private val runtime: MapRuntimeRegistry,
  private val producer: MapEventProducer,
  private val metrics: DistributedMapMetrics,
  private val clock: Clock,
) : SmartLifecycle {

  private val log = LoggerFactory.getLogger(TombstoneCleaner::class.java)
  private val running = AtomicBoolean(false)
  private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "dhm-tombstone-cleaner").apply { isDaemon = true }
  }

  override fun start() {
    if (!properties.cleanup.enabled) {
      log.info("Tombstone cleanup disabled by configuration")
      return
    }
    if (!running.compareAndSet(false, true)) return
    val intervalMs = properties.cleanup.interval.toMillis()
    executor.scheduleAtFixedRate(::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    log.info(
      "Tombstone cleaner started: interval={}, retention={}, publishKafkaTombstone={}",
      properties.cleanup.interval,
      properties.cleanup.tombstoneRetention,
      properties.cleanup.publishCompactionTombstone,
    )
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return
    executor.shutdownNow()
  }

  override fun isRunning(): Boolean = running.get()

  private fun tick() {
    val now = clock.instant()
    runtime.all().forEach { (mapName, map) ->
      val state = map.localState
      val toEvict = state.tombstoneKeysOlderThan(now, properties.cleanup.tombstoneRetention)
      if (toEvict.isEmpty()) return@forEach

      if (properties.cleanup.publishCompactionTombstone) {
        toEvict.forEach { key -> producer.publishCompactionTombstone(mapName, key) }
      }

      val evicted = state.evictTombstonesOlderThan(now, properties.cleanup.tombstoneRetention)
      metrics.recordTombstoneEvictions(mapName, evicted)
      log.debug("Evicted {} tombstone(s) from map={}", evicted, mapName)
    }
  }
}
