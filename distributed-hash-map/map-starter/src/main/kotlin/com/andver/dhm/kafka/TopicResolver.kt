package com.andver.dhm.kafka

import com.andver.dhm.properties.DistributedMapProperties

/**
 * Maps a logical map name → Kafka topic name. Defaults to `distributed-map.<name>` so two demo
 * services configured with the same map name share data automatically.
 */
class TopicResolver(private val properties: DistributedMapProperties) {

  fun topicFor(mapName: String): String {
    val def = properties.maps[mapName]
      ?: error("No map definition found for name=$mapName")
    return def.topic ?: "distributed-map.$mapName"
  }

  fun mapNameFor(topic: String): String? {
    return properties.maps.entries.firstOrNull { (name, _) -> topicFor(name) == topic }?.key
  }

  fun allTopics(): List<String> = properties.maps.keys.map { topicFor(it) }
}
