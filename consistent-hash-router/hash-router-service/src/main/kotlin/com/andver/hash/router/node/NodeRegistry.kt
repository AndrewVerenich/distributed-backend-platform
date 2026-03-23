package com.andver.hash.router.node

import com.andver.hash.router.config.RouterProperties
import com.andver.hash.router.hash.ConsistentHashRing
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class NodeRegistry(
  private val routerProperties: RouterProperties,
  private val consistentHashRing: ConsistentHashRing,
) {
  private val allNodes = ConcurrentHashMap<String, BackendNode>()
  private val healthy = ConcurrentHashMap.newKeySet<String>()
  private val consecutiveFailures = ConcurrentHashMap<String, Int>()

  @PostConstruct
  fun initialize() {
    routerProperties.nodes.forEach {
      val node = BackendNode(it.id, it.host, it.port, it.weight)
      allNodes[node.id] = node
      healthy.add(node.id)
      consecutiveFailures[node.id] = 0
      consistentHashRing.addNode(node, routerProperties.virtualNodesPerNode)
    }
  }

  fun allNodes(): List<BackendNode> = allNodes.values.sortedBy { it.id }

  fun markHealthy(nodeId: String) {
    val node = allNodes[nodeId] ?: return
    val becameHealthy = healthy.add(node.id)
    consecutiveFailures[node.id] = 0
    if (becameHealthy) {
      consistentHashRing.addNode(node, routerProperties.virtualNodesPerNode)
    }
  }

  fun markFailure(nodeId: String) {
    val failures = consecutiveFailures.compute(nodeId) { _, current -> (current ?: 0) + 1 } ?: 0
    if (failures >= routerProperties.failureThreshold) {
      markUnhealthy(nodeId)
    }
  }

  fun markUnhealthy(nodeId: String) {
    val removed = healthy.remove(nodeId)
    if (removed) {
      consistentHashRing.removeNode(nodeId)
    }
  }

  fun status(): List<NodeStatus> {
    return allNodes().map {
      NodeStatus(
        id = it.id,
        baseUrl = it.baseUrl(),
        healthy = healthy.contains(it.id),
        consecutiveFailures = consecutiveFailures[it.id] ?: 0,
      )
    }
  }
}

data class NodeStatus(
  val id: String,
  val baseUrl: String,
  val healthy: Boolean,
  val consecutiveFailures: Int,
)
