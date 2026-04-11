package com.andver.bff.mobile.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bff.backend")
data class BackendProperties(
  val userServiceBaseUrl: String = "http://localhost:8091",
  val productServiceBaseUrl: String = "http://localhost:8092",
)
