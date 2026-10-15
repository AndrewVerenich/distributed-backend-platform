package com.andver.circuitbreaker.guarded

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoClientGuardedApp

fun main(args: Array<String>) {
  runApplication<DemoClientGuardedApp>(*args)
}
