package com.andver.gateway.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "routing")
data class RoutingProperties(
  val authService: String,
  val resourceService: String
)
