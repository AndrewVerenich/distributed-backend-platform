package com.andver.gateway.websocket

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class WebsocketGatewayApp

fun main(args: Array<String>) {
  SpringApplication.run(WebsocketGatewayApp::class.java, *args)
}
