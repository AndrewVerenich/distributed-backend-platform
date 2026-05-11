package com.andver.dhm.runtime

/**
 * Decides whether an incoming envelope should overwrite the existing local entry under LWW.
 *
 * Rules (in order):
 *  1. No existing entry → incoming wins.
 *  2. Strictly newer [LocalEntry.updatedAt] → incoming wins.
 *  3. Strictly older → incoming loses.
 *  4. Same timestamp (rare, but possible with coarse clocks) → deterministic tie-break by
 *     lexicographic [LocalEntry.sourceNodeId] comparison: the larger node id wins.
 *
 * The tie-breaker is symmetric across all replicas so that, given the same set of events,
 * every node converges to the same winner regardless of arrival order.
 */
object LwwResolver {

  fun shouldApply(existing: LocalEntry?, incoming: LocalEntry): Boolean {
    if (existing == null) return true
    val timeCmp = incoming.updatedAt.compareTo(existing.updatedAt)
    if (timeCmp > 0) return true
    if (timeCmp < 0) return false
    return incoming.sourceNodeId > existing.sourceNodeId
  }
}
