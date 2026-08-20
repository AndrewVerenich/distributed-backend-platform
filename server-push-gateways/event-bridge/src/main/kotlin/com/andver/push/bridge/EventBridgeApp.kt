package com.andver.push.bridge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventBridgeApp

fun main(args: Array<String>) {
  runApplication<EventBridgeApp>(*args)
}
