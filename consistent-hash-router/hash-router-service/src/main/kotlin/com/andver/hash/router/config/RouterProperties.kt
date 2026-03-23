package com.andver.hash.router.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "router")
data class RouterProperties(
  val virtualNodesPerNode: Int = 150,
  val backendServiceName: String = "stateful-backend",
  val syncIntervalMs: Long = 10000,
)
