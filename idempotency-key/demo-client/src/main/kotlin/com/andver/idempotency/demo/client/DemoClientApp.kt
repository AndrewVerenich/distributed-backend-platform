package com.andver.idempotency.demo.client

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoClientApp

fun main(args: Array<String>) {
  runApplication<DemoClientApp>(*args)
}
