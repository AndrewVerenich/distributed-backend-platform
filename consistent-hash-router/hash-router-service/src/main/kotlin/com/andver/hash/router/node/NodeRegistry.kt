package com.andver.hash.router.node

import com.andver.hash.router.config.RouterProperties
import com.andver.hash.router.hash.ConsistentHashRing
import com.andver.hash.router.metrics.RouterMetrics
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class NodeRegistry(
  private val routerProperties: RouterProperties,
  private val consistentHashRing: ConsistentHashRing,
  private val metrics: RouterMetrics,
) {
  private val allNodes = ConcurrentHashMap<String, BackendNode>()

  fun addNode(node: BackendNode) {
    val wasAbsent = allNodes.putIfAbsent(node.id, node) == null
    if (wasAbsent) {
      consistentHashRing.addNode(node, routerProperties.virtualNodesPerNode)
      metrics.nodeAdded()
    }
  }

  fun allNodes(): List<BackendNode> = allNodes.values.sortedBy { it.id }

  fun removeNode(nodeId: String) {
    val removed = allNodes.remove(nodeId)
    if (removed != null) {
      consistentHashRing.removeNode(nodeId)
      metrics.nodeRemoved()
    }
  }

  fun status(): List<NodeStatus> {
    return allNodes().map {
      NodeStatus(
        id = it.id,
        baseUrl = it.baseUrl(),
        healthy = true,
      )
    }
  }
}

data class NodeStatus(
  val id: String,
  val baseUrl: String,
  val healthy: Boolean,
)
