package com.andver.gateway.push.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(PushGatewayProperties::class)
class PushGatewayConfig

@ConfigurationProperties(prefix = "push.gateway")
data class PushGatewayProperties(
  val maxConnections: Int = 2000,
  val longPollTimeout: Duration = Duration.ofSeconds(30),
  val sseHeartbeatInterval: Duration = Duration.ofSeconds(15),
  val channelPrefix: String = "PUSH_CHANNEL",
  val replayPrefix: String = "PUSH_REPLAY",
  val replayMaxSize: Long = 100,
)
