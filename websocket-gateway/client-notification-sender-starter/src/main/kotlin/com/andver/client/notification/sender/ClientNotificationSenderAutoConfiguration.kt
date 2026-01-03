package com.andver.client.notification.sender

import com.andver.client.notification.model.server.DomainServerEvent
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@AutoConfiguration
class ClientNotificationSenderAutoConfiguration(
  private val kafkaProperties: KafkaProperties,
) {

  @Bean
  fun producerFactory(): ProducerFactory<String, DomainServerEvent<out Any>> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    props["spring.json.add.type.headers"] = false
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  fun kafkaTemplate(): KafkaTemplate<String, DomainServerEvent<out Any>> {
    return KafkaTemplate(producerFactory())
  }

  @Bean
  fun clientNotificationSender(): ClientNotificationSender {
    return DefaultClientNotificationSender(kafkaTemplate())
  }
}