package com.andver.example.server

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@SpringBootApplication
class SimpleServerMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleServerMicroserviceApp::class.java, *args)
}

@RestController
class TestController {
  private val logger = LoggerFactory.getLogger(TestController::class.java)

  @PostMapping("/test")
  fun test(): Mono<String> {
    logger.info("Test endpoint called")
    return Mono.just("test")
  }
}