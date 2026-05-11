package com.andver.dhm.admin

import com.andver.dhm.properties.DistributedMapProperties
import com.andver.dhm.readiness.MapReadinessTracker
import com.andver.dhm.runtime.MapRuntimeRegistry
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Diagnostics endpoint for the demo: expose per-map size, readiness and key snapshots so the
 * cross-node replication demo is observable from `curl`.
 *
 * Mounted at `${distributed.map.admin.base-path}` (default `/distributed-map`).
 */
@RestController
@RequestMapping("\${distributed.map.admin.base-path:/distributed-map}")
class DistributedMapAdminController(
  private val runtime: MapRuntimeRegistry,
  private val readiness: MapReadinessTracker,
  private val properties: DistributedMapProperties,
) {

  @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  fun status(): Map<String, Any> {
    val maps = runtime.all().mapValues { (name, map) ->
      mapOf(
        "valueType" to map.valueType.name,
        "size" to map.size(),
        "tombstones" to map.localState.tombstoneSize(),
        "applied" to map.localState.appliedCount(),
        "rejected" to map.localState.rejectedCount(),
        "ready" to readiness.isReady(name),
      )
    }
    return mapOf(
      "nodeId" to (properties.nodeId ?: "<random>"),
      "ready" to readiness.allReady(),
      "maps" to maps,
    )
  }

  @GetMapping("/{mapName}", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun mapSnapshot(@PathVariable mapName: String): Map<String, Any?> {
    val map = runtime.raw(mapName)
      ?: error("Unknown map=$mapName")
    return mapOf(
      "name" to mapName,
      "ready" to readiness.isReady(mapName),
      "entries" to map.snapshot(),
    )
  }
}
