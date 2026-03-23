package com.andver.hash.router.hash

import com.andver.hash.router.node.BackendNode
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

@Component
class ConsistentHashRing {
  private val hashFunction: HashFunction = MurmurHash3Function()
  private val ring = ConcurrentSkipListMap<Long, BackendNode>()
  private val nodeHashes = ConcurrentHashMap<String, MutableSet<Long>>()

  fun addNode(node: BackendNode, virtualNodes: Int) {
    val hashes = mutableSetOf<Long>()
    repeat(virtualNodes.coerceAtLeast(1) * node.weight.coerceAtLeast(1)) { replica ->
      val hash = hashFunction.hash("${node.id}#$replica")
      ring[hash] = node
      hashes.add(hash)
    }
    nodeHashes[node.id] = hashes
  }

  fun removeNode(nodeId: String) {
    val hashes = nodeHashes.remove(nodeId) ?: return
    hashes.forEach { ring.remove(it) }
  }

  fun resolveNode(key: String): BackendNode? {
    if (ring.isEmpty()) return null
    val hash = hashFunction.hash(key)
    return ring.ceilingEntry(hash)?.value ?: ring.firstEntry().value
  }

  fun virtualNodeCountByNodeId(): Map<String, Int> {
    return nodeHashes.mapValues { it.value.size }
  }
}
