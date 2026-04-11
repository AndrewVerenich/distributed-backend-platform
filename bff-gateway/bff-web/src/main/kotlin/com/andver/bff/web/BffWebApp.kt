package com.andver.bff.web

import com.andver.bff.web.config.BackendProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BackendProperties::class)
class BffWebApp

fun main(args: Array<String>) {
  runApplication<BffWebApp>(*args)
}
