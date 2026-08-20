package com.andver.push.sender

import com.andver.push.model.PushEvent
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@AutoConfiguration
@ConditionalOnProperty(prefix = "push.sender", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class PushSenderAutoConfiguration(
  private val kafkaProperties: KafkaProperties,
) {

  @Bean
  @ConditionalOnMissingBean(name = ["pushEventProducerFactory"])
  fun pushEventProducerFactory(): ProducerFactory<String, PushEvent> {
    val props = HashMap<String, Any>(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    props["spring.json.add.type.headers"] = false
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  @ConditionalOnMissingBean(name = ["pushEventKafkaTemplate"])
  fun pushEventKafkaTemplate(): KafkaTemplate<String, PushEvent> {
    return KafkaTemplate(pushEventProducerFactory())
  }

  @Bean
  @ConditionalOnMissingBean
  fun pushEventSender(): PushEventSender {
    return DefaultPushEventSender(pushEventKafkaTemplate())
  }
}
