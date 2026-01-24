package com.andver.timeservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TimeServiceApp

fun main(args: Array<String>) {
  runApplication<TimeServiceApp>(*args)
}

