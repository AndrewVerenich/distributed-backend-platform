package com.andver.saga.flight

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlightServiceApp

fun main(args: Array<String>) {
  runApplication<FlightServiceApp>(*args)
}
