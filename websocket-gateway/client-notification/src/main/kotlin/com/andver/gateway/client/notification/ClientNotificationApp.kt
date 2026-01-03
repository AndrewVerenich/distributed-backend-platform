package com.andver.gateway.client.notification

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class ClientNotificationApp

fun main(args: Array<String>) {
  SpringApplication.run(ClientNotificationApp::class.java, *args)
}
