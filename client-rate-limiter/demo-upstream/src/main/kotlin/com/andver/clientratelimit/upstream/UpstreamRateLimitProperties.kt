package com.andver.clientratelimit.upstream

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "upstream-rate-limit")
data class UpstreamRateLimitProperties(
  val rules: List<Rule> = emptyList(),
) {
  data class Rule(
    val name: String = "",
    val pathPrefixes: List<String> = emptyList(),
    val limit: Long = 10,
    val window: Duration = Duration.ofSeconds(1),
  )
}
