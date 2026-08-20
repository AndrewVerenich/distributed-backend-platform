package com.andver.gateway.push

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PushGatewayApp

fun main(args: Array<String>) {
  runApplication<PushGatewayApp>(*args)
}
