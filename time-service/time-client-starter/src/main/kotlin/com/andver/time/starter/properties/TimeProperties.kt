package com.andver.time.starter.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "time")
data class TimeProperties(
  val serviceUrl: String = "http://localhost:8080",
  val nodeId: String = "unknown",
  val sync: SyncProperties = SyncProperties()
)

data class SyncProperties(
  val enabled: Boolean = true,
  val intervalMs: Long = 30000
)