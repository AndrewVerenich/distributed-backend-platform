package com.andver.clientratelimit

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "client-rate-limiter")
data class ClientRateLimiterProperties(
  val enabled: Boolean = true,
  val keyPrefix: String = "crl",
  val maxWait: Duration = Duration.ofSeconds(30),
  val onRedisError: OnRedisError = OnRedisError.FAIL,
  val rules: List<RuleProperties> = emptyList(),
) {
  enum class OnRedisError {
    FAIL,
  }

  data class RuleProperties(
    val name: String = "",
    val pathPrefixes: List<String> = emptyList(),
    val limit: Long = 20,
    val window: Duration = Duration.ofSeconds(1),
    val burst: Long = 0,
  )
}
