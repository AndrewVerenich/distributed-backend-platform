package com.andver.push.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventProducerDemoApp

fun main(args: Array<String>) {
  runApplication<EventProducerDemoApp>(*args)
}
