package com.andver.auth.service.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
  val secret: String,
  val accessExpiration: Duration,
  val refreshExpiration: Duration
)
