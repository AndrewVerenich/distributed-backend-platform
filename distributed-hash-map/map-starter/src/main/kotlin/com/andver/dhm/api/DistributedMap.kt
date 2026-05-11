package com.andver.dhm.api

/**
 * Eventually-consistent distributed key-value map backed by a Kafka compacted topic.
 *
 * Semantics:
 *  - All writes are published as LWW envelopes to a per-map compacted topic.
 *  - Every node holds a full in-memory replica synchronized via a Kafka consumer.
 *  - Reads are local and lock-free (backed by [java.util.concurrent.ConcurrentHashMap]).
 *  - REMOVE produces a tombstone that is reconciled by LWW on remote nodes; tombstones
 *    are evicted asynchronously by the cleaner.
 *
 * The map is keyed by [String] for a portable Kafka key; values are de/serialized via Jackson
 * using the value type configured per map.
 */
interface DistributedMap<V : Any> {

  /** Logical name of this map; matches the underlying Kafka topic by default. */
  val name: String

  /** Java [Class] of the value type. Used to deserialize remote events. */
  val valueType: Class<V>

  /** Returns the current local value for [key] or `null` if the key is absent or tombstoned. */
  fun get(key: String): V?

  /**
   * Publishes a PUT event for [key]→[value] and applies it locally immediately (write-through).
   * The event is then re-broadcast to every other node via the compacted topic.
   */
  fun put(key: String, value: V)

  /**
   * Publishes a REMOVE event (tombstone) for [key] and applies it locally immediately.
   * The tombstone is later GC'd by [com.andver.dhm.cleanup.TombstoneCleaner].
   */
  fun remove(key: String)

  /** Returns `true` iff the local replica currently holds a non-tombstoned value for [key]. */
  fun containsKey(key: String): Boolean

  /** Number of live (non-tombstoned) entries in the local replica. */
  fun size(): Int

  /** Snapshot of all live entries; intended for diagnostics and demo, not high-throughput paths. */
  fun snapshot(): Map<String, V>
}
