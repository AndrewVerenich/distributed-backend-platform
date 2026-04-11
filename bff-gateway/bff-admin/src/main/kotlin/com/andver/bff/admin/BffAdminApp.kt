package com.andver.bff.admin

import com.andver.bff.admin.config.BackendProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BackendProperties::class)
class BffAdminApp

fun main(args: Array<String>) {
  runApplication<BffAdminApp>(*args)
}
