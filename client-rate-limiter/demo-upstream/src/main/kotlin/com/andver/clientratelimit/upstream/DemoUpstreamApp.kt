package com.andver.clientratelimit.upstream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(UpstreamRateLimitProperties::class)
class DemoUpstreamApp

fun main(args: Array<String>) {
  runApplication<DemoUpstreamApp>(*args)
}
