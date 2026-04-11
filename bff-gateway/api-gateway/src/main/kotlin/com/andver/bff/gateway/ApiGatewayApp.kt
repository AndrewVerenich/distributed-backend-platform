package com.andver.bff.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ApiGatewayApp

fun main(args: Array<String>) {
  runApplication<ApiGatewayApp>(*args)
}
