package com.andver.dhm.kafka

import com.andver.dhm.envelope.MapEvent
import com.andver.dhm.envelope.MapEventCodec
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

/**
 * Publishes envelopes (and Kafka log-compaction tombstones) onto per-map topics.
 *
 * The Kafka record key is always the map key, so per-key ordering inside a single partition is
 * preserved. Cross-partition ordering is intentionally not guaranteed — LWW reconciles it.
 */
class MapEventProducer(
  private val kafkaTemplate: KafkaTemplate<String, String>,
  private val codec: MapEventCodec,
  private val topicResolver: TopicResolver,
) {

  private val log = LoggerFactory.getLogger(MapEventProducer::class.java)

  fun publish(event: MapEvent) {
    val topic = topicResolver.topicFor(event.mapName)
    val payload = codec.encodeEvent(event)
    kafkaTemplate.send(topic, event.key, payload)
    log.debug(
      "Published event map={} key={} op={} ts={} from node={}",
      event.mapName, event.key, event.operation, event.updatedAt, event.sourceNodeId,
    )
  }

  /**
   * Emits a Kafka *compaction tombstone* (record value = `null`) so that the broker can
   * physically delete the key during the next log compaction pass. This is distinct from the
   * application-level REMOVE envelope: the REMOVE envelope is replicated state, the compaction
   * tombstone is purely a storage hint.
   */
  fun publishCompactionTombstone(mapName: String, key: String) {
    val topic = topicResolver.topicFor(mapName)
    kafkaTemplate.send(ProducerRecord(topic, key, null))
    log.debug("Published Kafka compaction-tombstone map={} key={}", mapName, key)
  }
}
