package com.andver.application.events.config

import org.apache.camel.component.kafka.KafkaComponent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component

@Component
class KafkaConfig(
  @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
) {
  @Bean("debeziumKafka")
  fun debeziumKafkaComponent(): KafkaComponent {
    val component = KafkaComponent()
    component.configuration.apply {
      brokers = bootstrapServers
      keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer"
      valueDeserializer = "com.andver.application.events.deserializer.DebeziumJsonDeserializer"

      additionalProperties = mapOf(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringDeserializer",
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to "com.andver.application.events.deserializer.DebeziumJsonDeserializer"
      )
    }
    return component
  }

  @Bean("domainKafka")
  fun domainKafkaComponent(): KafkaComponent {
    val component = KafkaComponent()
    component.configuration.apply {
      brokers = bootstrapServers
      keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer"
      valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer"
    }
    return component
  }
}