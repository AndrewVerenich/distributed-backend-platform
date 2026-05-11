package com.andver.dhm.runtime

import com.andver.dhm.envelope.MapEvent
import com.andver.dhm.envelope.MapEventCodec
import com.andver.dhm.envelope.Operation
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class LocalState<V : Any>(
  private val mapName: String,
  private val valueType: Class<V>,
  private val codec: MapEventCodec,
) {

  private val store = ConcurrentHashMap<String, LocalEntry>()

  private val liveCount = AtomicLong(0)
  private val tombstoneCount = AtomicLong(0)
  private val appliedEvents = AtomicLong(0)
  private val rejectedEvents = AtomicLong(0)

  fun apply(event: MapEvent): Boolean {
    require(event.mapName == mapName) {
      "Event for map=${event.mapName} routed into LocalState for map=$mapName"
    }

    var changed = false
    store.compute(event.key) { _, existing ->
      val incoming = toLocalEntry(event)
      if (!LwwResolver.shouldApply(existing, incoming)) {
        rejectedEvents.incrementAndGet()
        return@compute existing
      }

      changed = true
      adjustCountersOnReplace(existing, incoming)
      appliedEvents.incrementAndGet()
      incoming
    }
    return changed
  }

  fun get(key: String): V? {
    val entry = store[key] ?: return null
    return when (entry) {
      is LocalEntry.Value -> @Suppress("UNCHECKED_CAST") (entry.payload as V)
      is LocalEntry.Tombstone -> null
    }
  }

  fun containsLive(key: String): Boolean = store[key] is LocalEntry.Value

  fun liveSize(): Int = liveCount.get().toInt()

  fun tombstoneSize(): Int = tombstoneCount.get().toInt()

  fun appliedCount(): Long = appliedEvents.get()

  fun rejectedCount(): Long = rejectedEvents.get()

  fun snapshot(): Map<String, V> {
    val out = HashMap<String, V>(store.size)
    store.forEach { (k, v) ->
      if (v is LocalEntry.Value) {
        @Suppress("UNCHECKED_CAST")
        out[k] = v.payload as V
      }
    }
    return out
  }

  fun evictTombstonesOlderThan(now: Instant, retention: Duration): Int {
    val cutoff = now.minus(retention)
    var evicted = 0
    store.forEach { (key, entry) ->
      if (entry is LocalEntry.Tombstone && entry.updatedAt.isBefore(cutoff)) {
        val removed = store.remove(key, entry)
        if (removed) {
          tombstoneCount.decrementAndGet()
          evicted++
        }
      }
    }
    return evicted
  }

  fun tombstoneKeysOlderThan(now: Instant, retention: Duration): List<String> {
    val cutoff = now.minus(retention)
    val out = ArrayList<String>()
    store.forEach { (key, entry) ->
      if (entry is LocalEntry.Tombstone && entry.updatedAt.isBefore(cutoff)) {
        out.add(key)
      }
    }
    return out
  }

  private fun toLocalEntry(event: MapEvent): LocalEntry = when (event.operation) {
    Operation.PUT -> {
      val payload = checkNotNull(event.valueJson) { "PUT envelope for key=${event.key} has null value" }
      LocalEntry.Value(
        payload = codec.decodeValue(payload, valueType),
        updatedAt = event.updatedAt,
        sourceNodeId = event.sourceNodeId,
      )
    }

    Operation.REMOVE -> LocalEntry.Tombstone(
      updatedAt = event.updatedAt,
      sourceNodeId = event.sourceNodeId,
    )
  }

  private fun adjustCountersOnReplace(existing: LocalEntry?, incoming: LocalEntry) {
    val wasLive = existing is LocalEntry.Value
    val wasTomb = existing is LocalEntry.Tombstone
    val isLive = incoming is LocalEntry.Value
    val isTomb = incoming is LocalEntry.Tombstone

    if (!wasLive && isLive) liveCount.incrementAndGet()
    if (wasLive && !isLive) liveCount.decrementAndGet()
    if (!wasTomb && isTomb) tombstoneCount.incrementAndGet()
    if (wasTomb && !isTomb) tombstoneCount.decrementAndGet()
  }
}
