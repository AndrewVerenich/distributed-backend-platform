package com.andver.dhm.kafka

import com.andver.dhm.api.DistributedMap
import com.andver.dhm.api.DistributedMapRegistry
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.time.Duration

/**
 * End-to-end test: two Spring Boot contexts (= two simulated nodes) share an embedded Kafka
 * broker and both back the same `user-cache` map. Verifies:
 *
 *  - cross-node propagation of PUT (write on A → visible on B);
 *  - cross-node propagation of REMOVE (tombstone replication);
 *  - LWW resolution: writes from `node-z` beat earlier writes from `node-a`;
 *  - bootstrap restore: a third node started later replays the entire compacted topic.
 *
 * We use [EmbeddedKafkaBroker] (in-process) instead of Testcontainers so the test works without
 * a Docker daemon. The behaviour exercised by the test — log compaction semantics aside — is
 * identical to a real Kafka broker.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistributedMapKafkaIntegrationTest {

  data class Profile(val name: String, val tier: String = "standard")

  @SpringBootApplication
  open class TestApp

  companion object {
    @JvmStatic
    val broker: EmbeddedKafkaBroker = EmbeddedKafkaBroker(
      /* count = */ 1,
      /* controlledShutdown = */ true,
      /* partitions = */ 1,
    )

    init {
      broker.afterPropertiesSet()
    }
  }

  private lateinit var nodeA: ConfigurableApplicationContext
  private lateinit var nodeB: ConfigurableApplicationContext

  private lateinit var mapA: DistributedMap<Profile>
  private lateinit var mapB: DistributedMap<Profile>

  @BeforeAll
  fun startNodes() {
    nodeA = bootNode("node-a")
    nodeB = bootNode("node-z") // 'z' > 'a' lexicographically — convenient for tie-break checks
    mapA = nodeA.getBean(DistributedMapRegistry::class.java).get("user-cache", Profile::class.java)
    mapB = nodeB.getBean(DistributedMapRegistry::class.java).get("user-cache", Profile::class.java)
  }

  @AfterAll
  fun stopNodes() {
    nodeA.close()
    nodeB.close()
    broker.destroy()
  }

  @Test
  fun `put on node A is visible on node B`() {
    mapA.put("u-cross-1", Profile("Alice", "gold"))

    awaitUntil { mapB.get("u-cross-1") == Profile("Alice", "gold") }
  }

  @Test
  fun `remove on node A also tombstones node B`() {
    mapA.put("u-cross-2", Profile("Bob"))
    awaitUntil { mapB.containsKey("u-cross-2") }

    mapA.remove("u-cross-2")

    awaitUntil { mapB.get("u-cross-2") == null }
    assertNull(mapB.get("u-cross-2"))
  }

  @Test
  fun `LWW resolves concurrent writes deterministically`() {
    mapA.put("u-lww", Profile("AliceA"))
    Thread.sleep(50) // ensure node-z's timestamp is strictly larger
    mapB.put("u-lww", Profile("AliceB"))

    awaitUntil { mapA.get("u-lww") == Profile("AliceB") }
    assertEquals(Profile("AliceB"), mapA.get("u-lww"))
    assertEquals(Profile("AliceB"), mapB.get("u-lww"))
  }

  @Test
  fun `late starting node restores state from compacted topic`() {
    mapA.put("u-bootstrap-1", Profile("BootA"))
    mapA.put("u-bootstrap-2", Profile("BootB"))
    awaitUntil { mapB.containsKey("u-bootstrap-1") && mapB.containsKey("u-bootstrap-2") }

    val nodeC = bootNode("node-c-bootstrap")
    try {
      val mapC = nodeC.getBean(DistributedMapRegistry::class.java)
        .get("user-cache", Profile::class.java)
      awaitUntil { mapC.get("u-bootstrap-1") == Profile("BootA") }
      awaitUntil { mapC.get("u-bootstrap-2") == Profile("BootB") }
    } finally {
      nodeC.close()
    }
  }

  private fun bootNode(nodeId: String): ConfigurableApplicationContext {
    val app = SpringApplication(TestApp::class.java)
    app.setDefaultProperties(
      mapOf(
        "server.port" to 0,
        "spring.application.name" to "dhm-test-$nodeId",
        "spring.main.web-application-type" to "none",
        "spring.kafka.bootstrap-servers" to broker.brokersAsString,
        "distributed.map.enabled" to "true",
        "distributed.map.node-id" to nodeId,
        "distributed.map.bootstrap.timeout" to "30s",
        "distributed.map.cleanup.enabled" to "false",
        "distributed.map.maps.user-cache.value-type" to Profile::class.java.name,
        "distributed.map.maps.user-cache.partitions" to "1",
        "distributed.map.maps.user-cache.replication-factor" to "1",
      ),
    )
    return app.run()
  }

  private fun awaitUntil(condition: () -> Boolean) {
    Awaitility.await()
      .atMost(Duration.ofSeconds(30))
      .pollInterval(Duration.ofMillis(150))
      .until(condition)
  }
}
