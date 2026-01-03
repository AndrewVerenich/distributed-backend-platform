package com.andver.client.notification.sender

import com.andver.client.notification.model.server.DomainServerEvent
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@AutoConfiguration
class ClientNotificationSenderAutoConfiguration(
  private val kafkaProperties: KafkaProperties,
) {

  @Bean
  fun producerFactory(): ProducerFactory<String, DomainServerEvent<out Any>> {
    return DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties())
  }

  @Bean
  fun kafkaTemplate(): KafkaTemplate<String, DomainServerEvent<out Any>> {
    return KafkaTemplate(producerFactory())
  }
}