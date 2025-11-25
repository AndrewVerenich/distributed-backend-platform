package com.andver.dynamicconfig.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dynamic-config")
data class DynamicConfigProperties(
  var bootstrapServers: String = "localhost:9092",
  var topic: String = "config-state",
)