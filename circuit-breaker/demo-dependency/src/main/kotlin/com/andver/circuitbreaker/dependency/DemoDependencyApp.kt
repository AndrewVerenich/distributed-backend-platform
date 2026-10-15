package com.andver.circuitbreaker.dependency

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DemoDependencyApp

fun main(args: Array<String>) {
  runApplication<DemoDependencyApp>(*args)
}
