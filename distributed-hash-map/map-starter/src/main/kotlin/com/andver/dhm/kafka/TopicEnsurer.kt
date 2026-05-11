package com.andver.dhm.kafka

import com.andver.dhm.properties.DistributedMapProperties
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

/**
 * Idempotent compacted-topic creation for every configured map.
 *
 * If the topic already exists with a non-compact policy, we log a warning rather than failing
 * the application: switching cleanup.policy on a live topic must be an operator decision.
 */
class TopicEnsurer(
  private val adminClient: AdminClient,
  private val properties: DistributedMapProperties,
  private val topicResolver: TopicResolver,
) {

  private val log = LoggerFactory.getLogger(TopicEnsurer::class.java)

  fun ensureAllTopics() {
    properties.maps.forEach { (name, def) ->
      ensureTopic(
        topic = topicResolver.topicFor(name),
        partitions = def.partitions,
        replicationFactor = def.replicationFactor,
      )
    }
  }

  private fun ensureTopic(topic: String, partitions: Int, replicationFactor: Short) {
    val newTopic = NewTopic(topic, partitions, replicationFactor)
      .configs(
        mapOf(
          TopicConfig.CLEANUP_POLICY_CONFIG to TopicConfig.CLEANUP_POLICY_COMPACT,
          TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG to "0.1",
          TopicConfig.SEGMENT_MS_CONFIG to "60000",
          TopicConfig.DELETE_RETENTION_MS_CONFIG to "60000",
        ),
      )

    try {
      adminClient.createTopics(listOf(newTopic)).all().get()
      log.info("Created compacted topic={} partitions={} rf={}", topic, partitions, replicationFactor)
    } catch (e: ExecutionException) {
      if (e.cause is TopicExistsException) {
        log.info("Topic={} already exists, skipping creation", topic)
      } else {
        throw IllegalStateException("Failed to ensure topic=$topic", e)
      }
    }
  }
}
