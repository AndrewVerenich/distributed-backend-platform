package com.andver.hash.router.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "router")
data class RouterProperties(
  val virtualNodesPerNode: Int = 150,
  val healthCheckEnabled: Boolean = true,
  val healthCheckDelayMs: Long = 5000,
  val healthCheckTimeoutMs: Long = 1000,
  val failureThreshold: Int = 3,
  val nodes: List<RouterNodeProperties> = emptyList(),
)

data class RouterNodeProperties(
  val id: String,
  val host: String,
  val port: Int,
  val weight: Int = 1,
)
