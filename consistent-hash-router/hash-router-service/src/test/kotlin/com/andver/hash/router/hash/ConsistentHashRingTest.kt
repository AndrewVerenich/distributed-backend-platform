package com.andver.hash.router.hash

import com.andver.hash.router.node.BackendNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConsistentHashRingTest {
  @Test
  fun `distribution across three nodes is reasonably even`() {
    val ring = ConsistentHashRing()
    val nodes = listOf(
      BackendNode("n1", "localhost", 8081),
      BackendNode("n2", "localhost", 8082),
      BackendNode("n3", "localhost", 8083),
    )
    nodes.forEach { ring.addNode(it, 150) }

    val counts = mutableMapOf<String, Int>()
    repeat(10_000) { idx ->
      val node = ring.resolveNode("user-$idx")
      counts.merge(node!!.id, 1, Int::plus)
    }

    counts.values.forEach { count ->
      assertThat(count).isBetween(2_600, 4_000)
    }
  }

  @Test
  fun `removing one node remaps only subset of keys`() {
    val ring = ConsistentHashRing()
    val n1 = BackendNode("n1", "localhost", 8081)
    val n2 = BackendNode("n2", "localhost", 8082)
    val n3 = BackendNode("n3", "localhost", 8083)
    listOf(n1, n2, n3).forEach { ring.addNode(it, 150) }

    val before = (0 until 10_000).associateWith { key -> ring.resolveNode("k-$key")!!.id }
    ring.removeNode(n3.id)
    val after = (0 until 10_000).associateWith { key -> ring.resolveNode("k-$key")!!.id }

    val changed = before.keys.count { before[it] != after[it] }.toDouble() / before.size.toDouble()
    assertThat(changed).isBetween(0.20, 0.50)
  }

  @Test
  fun `adding a new node remaps only subset of keys`() {
    val ring = ConsistentHashRing()
    listOf(
      BackendNode("n1", "localhost", 8081),
      BackendNode("n2", "localhost", 8082),
      BackendNode("n3", "localhost", 8083),
    ).forEach { ring.addNode(it, 150) }

    val before = (0 until 10_000).associateWith { key -> ring.resolveNode("k-$key")!!.id }
    ring.addNode(BackendNode("n4", "localhost", 8084), 150)
    val after = (0 until 10_000).associateWith { key -> ring.resolveNode("k-$key")!!.id }

    val changed = before.keys.count { before[it] != after[it] }.toDouble() / before.size.toDouble()
    assertThat(changed).isBetween(0.15, 0.40)
  }

  @Test
  fun `single node ring always resolves to that node`() {
    val ring = ConsistentHashRing()
    val onlyNode = BackendNode("n1", "localhost", 8081)
    ring.addNode(onlyNode, 1)

    repeat(1_000) {
      assertThat(ring.resolveNode("any-$it")).isEqualTo(onlyNode)
    }
  }
}
