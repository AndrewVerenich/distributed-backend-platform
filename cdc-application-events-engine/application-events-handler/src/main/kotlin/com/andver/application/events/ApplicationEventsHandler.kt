package com.andver.application.events

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class ApplicationEventsHandler

fun main(args: Array<String>) {
  SpringApplication.run(ApplicationEventsHandler::class.java, *args)
}