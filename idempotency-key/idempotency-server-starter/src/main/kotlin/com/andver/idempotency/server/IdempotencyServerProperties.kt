package com.andver.idempotency.server

import com.andver.idempotency.api.IdempotencyHeaders
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "idempotency.server")
data class IdempotencyServerProperties(
  val enabled: Boolean = true,
  val lease: Duration = Duration.ofSeconds(30),
  val retention: Duration = Duration.ofHours(24),
  val maxBodyBytes: Int = 256 * 1024,
  val paths: List<String> = emptyList(),
  val ownerHeader: String = IdempotencyHeaders.OWNER,
)
