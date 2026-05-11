package com.andver.dhm.readiness

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks whether each map has finished its bootstrap restore from the compacted topic.
 *
 * The actuator readiness indicator reads this state, so Kubernetes / load balancers can avoid
 * sending traffic to a node that has only partially-restored caches.
 */
class MapReadinessTracker(mapNames: Set<String>) {

  private val readyByMap: ConcurrentHashMap<String, Boolean> =
    ConcurrentHashMap<String, Boolean>().apply {
      mapNames.forEach { put(it, false) }
    }

  fun markReady(mapName: String) {
    readyByMap.computeIfPresent(mapName) { _, _ -> true }
  }

  fun markAllReadyIfNoMaps() {
    if (readyByMap.isEmpty()) return
  }

  fun isReady(mapName: String): Boolean = readyByMap[mapName] == true

  fun allReady(): Boolean = readyByMap.values.all { it }

  fun snapshot(): Map<String, Boolean> = HashMap(readyByMap)
}
