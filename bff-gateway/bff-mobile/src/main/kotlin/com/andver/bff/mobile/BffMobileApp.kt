package com.andver.bff.mobile

import com.andver.bff.mobile.config.BackendProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BackendProperties::class)
class BffMobileApp

fun main(args: Array<String>) {
  runApplication<BffMobileApp>(*args)
}
