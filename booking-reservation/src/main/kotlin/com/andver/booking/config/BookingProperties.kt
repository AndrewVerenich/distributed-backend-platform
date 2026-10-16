package com.andver.booking.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "booking")
data class BookingProperties(
  val holdTtl: Duration = Duration.ofSeconds(5),
  val sweeperIntervalMs: Long = 1000,
  val outboxIntervalMs: Long = 2000,
  val idempotency: IdempotencyProperties = IdempotencyProperties(),
) {
  data class IdempotencyProperties(
    val lease: Duration = Duration.ofSeconds(30),
    val retention: Duration = Duration.ofHours(24),
  )
}
