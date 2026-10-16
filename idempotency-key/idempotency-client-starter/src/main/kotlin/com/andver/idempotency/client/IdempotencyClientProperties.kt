package com.andver.idempotency.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "idempotency.client")
data class IdempotencyClientProperties(
  val enabled: Boolean = true,
  val maxRetries: Int = 3,
  val operationTtl: Duration = Duration.ofHours(1),
  val defaultRetryDelay: Duration = Duration.ofMillis(200),
)
