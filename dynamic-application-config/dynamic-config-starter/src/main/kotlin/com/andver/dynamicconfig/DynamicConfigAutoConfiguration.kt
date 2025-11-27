package com.andver.dynamicconfig

import com.andver.dynamicconfig.actuator.ConfigEndpoint
import com.andver.dynamicconfig.consumer.ConfigChangeConsumer
import com.andver.dynamicconfig.producer.ConfigStateProducer
import com.andver.dynamicconfig.properties.DynamicConfigProperties
import com.andver.dynamicconfig.storage.DefaultDynamicConfigStorage
import com.andver.dynamicconfig.storage.DynamicConfigStorage
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(
  prefix = "dynamic-config",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = false
)
@EnableConfigurationProperties(DynamicConfigProperties::class)
class DynamicConfigAutoConfiguration {

  @Bean
  fun storage(): DynamicConfigStorage = DefaultDynamicConfigStorage()

  @Bean
  fun configEndpoint(storage: DynamicConfigStorage): ConfigEndpoint = ConfigEndpoint(storage)

  @Bean
  fun configChangeConsumer(storage: DefaultDynamicConfigStorage): ConfigChangeConsumer = ConfigChangeConsumer(storage)

  @Bean
  fun configStateProducer(
    storage: DefaultDynamicConfigStorage,
    kafkaTemplate: KafkaTemplate<String, String>,
    objectMapper: ObjectMapper,
    @Value("\${spring.application.name}") appName: String,
    @Value("\${dynamic-config.snapshot-topic}") snapshotTopic: String,
  ): ConfigStateProducer = ConfigStateProducer(
    storage,
    kafkaTemplate,
    objectMapper,
    appName,
    snapshotTopic
  )
}