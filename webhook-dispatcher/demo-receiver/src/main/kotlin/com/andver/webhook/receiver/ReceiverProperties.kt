package com.andver.webhook.receiver

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "receiver")
data class ReceiverProperties(
  val pathSecrets: Map<String, String> = emptyMap(),
  val slowDelayMs: Long = 10_000,
)
