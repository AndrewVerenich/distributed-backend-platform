package com.andver.circuitbreaker.dumb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoClientDumbApp

fun main(args: Array<String>) {
  runApplication<DemoClientDumbApp>(*args)
}
