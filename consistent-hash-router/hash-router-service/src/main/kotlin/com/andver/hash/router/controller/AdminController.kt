package com.andver.hash.router.controller

import com.andver.hash.router.hash.ConsistentHashRing
import com.andver.hash.router.node.NodeRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
  private val nodeRegistry: NodeRegistry,
  private val consistentHashRing: ConsistentHashRing,
) {
  @GetMapping("/nodes")
  fun nodes() = nodeRegistry.status()

  @GetMapping("/ring/stats")
  fun ringStats() = consistentHashRing.virtualNodeCountByNodeId()

  @GetMapping("/ring/lookup")
  fun lookup(@RequestParam key: String): LookupResponse {
    val node = consistentHashRing.resolveNode(key)
    return LookupResponse(key, node?.id, node?.baseUrl())
  }
}

data class LookupResponse(
  val key: String,
  val nodeId: String?,
  val baseUrl: String?,
)
