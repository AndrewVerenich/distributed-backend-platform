package com.andver.clientratelimit.upstream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import java.time.Duration

@SpringBootApplication
@EnableConfigurationProperties(UpstreamRateLimitProperties::class)
class DemoUpstreamApp

fun main(args: Array<String>) {
  runApplication<DemoUpstreamApp>(*args)
}

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
