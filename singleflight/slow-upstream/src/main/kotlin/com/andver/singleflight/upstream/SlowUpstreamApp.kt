package com.andver.singleflight.upstream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SlowUpstreamApp

fun main(args: Array<String>) {
  runApplication<SlowUpstreamApp>(*args)
}
