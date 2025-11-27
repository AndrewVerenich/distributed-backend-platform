package com.andver.dynamicconfig.producer

import com.andver.dynamicconfig.storage.DefaultDynamicConfigStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.*

class ConfigStateProducer(
  private val storage: DefaultDynamicConfigStorage,
  private val kafkaTemplate: KafkaTemplate<String, String>,
  private val objectMapper: ObjectMapper,
  private val appName: String,
  private val snapshotTopic: String,
) {
  private val log = LoggerFactory.getLogger(ConfigStateProducer::class.java)
  private val nodeUuid = UUID.randomUUID().toString()

  @Scheduled(fixedRateString = "\${dynamic-config.state-send-interval-ms:10000}")
  fun sendState() {
    val key = "$appName-$nodeUuid"
    val snapshot = mapOf(
      "appName" to key,
      "timestamp" to Instant.now().toEpochMilli(),
      "configs" to storage.getAll()
    )
    val json = objectMapper.writeValueAsString(snapshot)
    log.debug("Sending state: $json")
    kafkaTemplate.send(ProducerRecord(snapshotTopic, key, json))
  }
}
