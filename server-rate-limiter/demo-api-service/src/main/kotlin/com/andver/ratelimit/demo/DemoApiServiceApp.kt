package com.andver.ratelimit.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoApiServiceApp

fun main(args: Array<String>) {
  runApplication<DemoApiServiceApp>(*args)
}
