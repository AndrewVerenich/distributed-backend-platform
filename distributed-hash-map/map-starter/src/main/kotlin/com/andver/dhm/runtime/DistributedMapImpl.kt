package com.andver.dhm.runtime

import com.andver.dhm.api.DistributedMap
import com.andver.dhm.envelope.MapEvent
import com.andver.dhm.envelope.MapEventCodec
import com.andver.dhm.envelope.Operation
import com.andver.dhm.kafka.MapEventProducer
import com.andver.dhm.metrics.DistributedMapMetrics
import java.time.Clock
import java.time.Instant

class DistributedMapImpl<V : Any>(
  override val name: String,
  override val valueType: Class<V>,
  private val nodeId: String,
  private val clock: Clock,
  private val codec: MapEventCodec,
  private val producer: MapEventProducer,
  private val metrics: DistributedMapMetrics,
  internal val localState: LocalState<V>,
) : DistributedMap<V> {

  override fun get(key: String): V? = localState.get(key)

  override fun put(key: String, value: V) {
    val event = MapEvent(
      mapName = name,
      key = key,
      operation = Operation.PUT,
      valueJson = codec.encodeValue(value),
      updatedAt = Instant.now(clock),
      sourceNodeId = nodeId,
    )
    localState.apply(event)
    producer.publish(event)
    metrics.recordPublish(name, Operation.PUT)
  }

  override fun remove(key: String) {
    val event = MapEvent(
      mapName = name,
      key = key,
      operation = Operation.REMOVE,
      valueJson = null,
      updatedAt = Instant.now(clock),
      sourceNodeId = nodeId,
    )
    localState.apply(event)
    producer.publish(event)
    metrics.recordPublish(name, Operation.REMOVE)
  }

  override fun containsKey(key: String): Boolean = localState.containsLive(key)

  override fun size(): Int = localState.liveSize()

  override fun snapshot(): Map<String, V> = localState.snapshot()
}
