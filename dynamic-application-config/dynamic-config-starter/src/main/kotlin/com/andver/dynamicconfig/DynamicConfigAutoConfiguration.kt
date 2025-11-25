package com.andver.dynamicconfig

import com.andver.dynamicconfig.actuator.ConfigEndpoint
import com.andver.dynamicconfig.consumer.KafkaConsumer
import com.andver.dynamicconfig.properties.DynamicConfigProperties
import com.andver.dynamicconfig.storage.DefaultDynamicConfigStorage
import com.andver.dynamicconfig.storage.DynamicConfigStorage
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
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
  internal fun kafkaConsumer(storage: DefaultDynamicConfigStorage): KafkaConsumer = KafkaConsumer(storage)
}