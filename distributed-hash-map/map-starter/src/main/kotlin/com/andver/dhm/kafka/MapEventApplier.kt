package com.andver.dhm.kafka

import com.andver.dhm.envelope.MapEvent
import com.andver.dhm.envelope.MapEventCodec
import com.andver.dhm.metrics.DistributedMapMetrics
import com.andver.dhm.runtime.MapRuntimeRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

/**
 * Routes incoming Kafka records to the correct per-map [com.andver.dhm.runtime.LocalState].
 *
 * Records with `value = null` are Kafka compaction tombstones (not application REMOVEs) and are
 * silently skipped: the broker will reclaim disk space on its own.
 *
 * Records that fail to decode are logged and skipped — we deliberately do not fail the consumer
 * loop on poison messages.
 */
class MapEventApplier(
  private val codec: MapEventCodec,
  private val runtime: MapRuntimeRegistry,
  private val topicResolver: TopicResolver,
  private val metrics: DistributedMapMetrics,
) {

  private val log = LoggerFactory.getLogger(MapEventApplier::class.java)

  fun apply(record: ConsumerRecord<String, String?>) {
    val value = record.value()
    if (value == null) {
      // Kafka compaction-tombstone — nothing to apply at the application layer.
      return
    }

    val event: MapEvent = try {
      codec.decodeEvent(value)
    } catch (e: Exception) {
      log.warn(
        "Skipping un-parseable record topic={} partition={} offset={}: {}",
        record.topic(), record.partition(), record.offset(), e.message,
      )
      return
    }

    val mapName = topicResolver.mapNameFor(record.topic())
      ?: event.mapName
    val state = runtime.localStateOrNull(mapName)
    if (state == null) {
      log.warn("Received event for unknown map={} (topic={})", mapName, record.topic())
      return
    }

    val changed = state.apply(event)
    metrics.recordEventApplied(mapName, changed)
  }
}
