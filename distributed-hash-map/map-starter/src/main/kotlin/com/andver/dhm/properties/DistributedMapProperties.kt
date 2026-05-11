package com.andver.dhm.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Top-level configuration:
 *
 * ```yaml
 * distributed:
 *   map:
 *     enabled: true
 *     node-id: ${HOSTNAME}
 *     bootstrap:
 *       timeout: 60s
 *     cleanup:
 *       interval: 60s
 *       tombstone-retention: 5m
 *     maps:
 *       user-cache:
 *         value-type: com.example.UserDto
 *         topic: distributed-map.user-cache       # default = "distributed-map.${name}"
 *         partitions: 3                            # only used when the topic is created
 *         replication-factor: 1
 * ```
 */
@ConfigurationProperties(prefix = "distributed.map")
data class DistributedMapProperties(
  val enabled: Boolean = true,
  /**
   * Stable identifier for this node. Used as the LWW tie-breaker source id and as part of the
   * Kafka consumer group id. If not set, a random UUID is generated at startup.
   */
  val nodeId: String? = null,
  val bootstrap: BootstrapProperties = BootstrapProperties(),
  val cleanup: CleanupProperties = CleanupProperties(),
  val admin: AdminProperties = AdminProperties(),
  val maps: Map<String, MapDefinitionProperties> = emptyMap(),
)

data class BootstrapProperties(
  /** Maximum time to wait for end-of-log to be reached on every map topic during startup restore. */
  val timeout: Duration = Duration.ofSeconds(60),
  /** Per-poll timeout used during the bootstrap loop. */
  val pollTimeout: Duration = Duration.ofMillis(500),
)

data class CleanupProperties(
  val enabled: Boolean = true,
  val interval: Duration = Duration.ofSeconds(60),
  val tombstoneRetention: Duration = Duration.ofMinutes(5),
  /**
   * When true, the cleaner also publishes a compaction-tombstone (Kafka record value = `null`)
   * for evicted keys so that Kafka log compaction can free disk space upstream.
   */
  val publishCompactionTombstone: Boolean = true,
)

data class AdminProperties(
  /** Expose REST endpoints under the configured base path for diagnostics. */
  val enabled: Boolean = true,
  val basePath: String = "/distributed-map",
)

data class MapDefinitionProperties(
  /** Fully-qualified Java class name of the value type. */
  val valueType: String,
  val topic: String? = null,
  val partitions: Int = 3,
  val replicationFactor: Short = 1,
)
