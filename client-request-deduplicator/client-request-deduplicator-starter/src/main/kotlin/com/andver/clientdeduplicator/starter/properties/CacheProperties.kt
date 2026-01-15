package com.andver.clientdeduplicator.starter.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "request.deduplicator")
data class CacheProperties(
  val enabled: Boolean = true,
  val rules: List<CacheRule> = emptyList()
)

data class CacheRule(
  val method: String,
  val url: String,
  val ttl: Duration,
  val excludeFields: Set<String> = emptySet(),
  val excludeQueryParams: Set<String> = emptySet()
)