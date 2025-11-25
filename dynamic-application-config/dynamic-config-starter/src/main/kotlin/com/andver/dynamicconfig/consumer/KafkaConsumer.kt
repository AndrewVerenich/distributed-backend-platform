package com.andver.dynamicconfig.consumer

import com.andver.dynamicconfig.storage.DefaultDynamicConfigStorage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
  prefix = "dynamic-config",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = false
)
internal class KafkaConsumer(
  private val storage: DefaultDynamicConfigStorage,
) {
  private val log = LoggerFactory.getLogger(KafkaConsumer::class.java)

  @KafkaListener(
    topics = ["\${dynamic-config.topic}"],
    groupId = "\${spring.application.name}-\${random.uuid}",
    properties = [
      "bootstrap.servers=\${dynamic-config.bootstrap-servers}",
      "auto.offset.reset=earliest"
    ]
  )
  fun consume(record: ConsumerRecord<String, String>) {
    storage.put(record.key(), record.value()).also {
      log.info("Updated config with key:${record.key()} and value:${record.value()}")
    }
  }
}
