package com.andver.dhm.runtime

import java.time.Instant

/**
 * Backing store entry: either a live value or a tombstone (logical deletion).
 *
 * Holding tombstones explicitly is what makes LWW correct in the face of out-of-order delivery:
 * a stale PUT (older [updatedAt]) that arrives *after* a REMOVE must be ignored, otherwise the
 * key would silently resurrect. Tombstones are GC'd by [com.andver.dhm.cleanup.TombstoneCleaner].
 */
sealed class LocalEntry {
  abstract val updatedAt: Instant
  abstract val sourceNodeId: String

  data class Value(
    val payload: Any,
    override val updatedAt: Instant,
    override val sourceNodeId: String,
  ) : LocalEntry()

  data class Tombstone(
    override val updatedAt: Instant,
    override val sourceNodeId: String,
  ) : LocalEntry()
}
