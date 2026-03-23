package com.andver.hash.router.health

import com.andver.hash.router.config.RouterProperties
import com.andver.hash.router.node.BackendNode
import com.andver.hash.router.node.NodeRegistry
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NodeHealthChecker(
  private val nodeRegistry: NodeRegistry,
  private val routerProperties: RouterProperties,
  private val discoveryClient: DiscoveryClient,
) {
  @Scheduled(fixedDelayString = "\${router.sync-interval-ms:10000}")
  fun checkNodes() {
    val discoveredNodes = discoveryClient.getInstances(routerProperties.backendServiceName)
      .map { toBackendNode(it) }
      .associateBy { it.id }

    val currentNodeIds = nodeRegistry.allNodes().map { it.id }.toSet()
    val discoveredNodeIds = discoveredNodes.keys

    discoveredNodes.values.forEach { nodeRegistry.addNode(it) }

    val removedNodeIds = currentNodeIds - discoveredNodeIds
    removedNodeIds.forEach { nodeRegistry.removeNode(it) }
  }

  private fun toBackendNode(instance: ServiceInstance): BackendNode {
    val nodeId = instance.metadata["backendId"]
      ?: instance.instanceId
      ?: "${instance.host}:${instance.port}"

    val weight = instance.metadata["weight"]?.toIntOrNull() ?: 1

    return BackendNode(
      id = nodeId,
      host = instance.host,
      port = instance.port,
      weight = weight,
    )
  }
}
