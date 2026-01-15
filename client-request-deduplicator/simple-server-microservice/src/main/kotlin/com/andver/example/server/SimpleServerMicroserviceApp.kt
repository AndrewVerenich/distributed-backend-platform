package com.andver.example.server

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@SpringBootApplication
class SimpleServerMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleServerMicroserviceApp::class.java, *args)
}

@RestController
class TestController {
  private val logger = LoggerFactory.getLogger(TestController::class.java)

  @PostMapping("/order")
  fun test(@RequestBody request: CreateOrderRequest): Mono<CreateOrderResponse> {
    logger.info("Create order request=$request")
    return Mono.just(CreateOrderResponse(20L))
  }
}

data class CreateOrderRequest(
  val productId: Long,
  val timestamp: LocalDateTime,
)

data class CreateOrderResponse(
  val orderId: Long,
)