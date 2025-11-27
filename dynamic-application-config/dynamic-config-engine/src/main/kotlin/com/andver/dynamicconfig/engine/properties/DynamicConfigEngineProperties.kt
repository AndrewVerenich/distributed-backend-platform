package com.andver.dynamicconfig.engine.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dynamic-config")
data class DynamicConfigEngineProperties(
  var topic: String,
  var debeziumTopic: String,
  var snapshotTopic: String,
  var windowSeconds: Long,
  var alertsTopic: String,
)

