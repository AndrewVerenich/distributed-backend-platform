package com.andver.webhook.receiver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ReceiverProperties::class)
class DemoReceiverApp

fun main(args: Array<String>) {
  runApplication<DemoReceiverApp>(*args)
}
