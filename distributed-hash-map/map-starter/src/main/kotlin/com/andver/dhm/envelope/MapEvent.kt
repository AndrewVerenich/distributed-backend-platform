package com.andver.dhm.envelope

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * On-the-wire LWW envelope written to the compacted topic.
 *
 *  - [valueJson] is the Jackson-serialized payload for PUTs and `null` for REMOVE tombstones.
 *  - [updatedAt] is the wall-clock timestamp used as the LWW comparator. Clock skew between
 *    nodes can affect the winner; this trade-off is documented in the README.
 *  - [sourceNodeId] breaks ties when two events share the same [updatedAt].
 *
 * Tombstones are represented as `Operation = REMOVE` with `valueJson = null`.
 * Real Kafka log compaction `null`-deletes happen only when the cleaner emits a
 * compaction-tombstone (record value = `null`).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MapEvent(
  val mapName: String,
  val key: String,
  val operation: Operation,
  val valueJson: String?,
  val updatedAt: Instant,
  val sourceNodeId: String,
)
