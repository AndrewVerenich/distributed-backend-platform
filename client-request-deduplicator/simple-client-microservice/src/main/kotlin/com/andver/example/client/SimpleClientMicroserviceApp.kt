package com.andver.example.client

import com.andver.clientdeduplicator.starter.inserter.capturedBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@SpringBootApplication
class SimpleClientMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleClientMicroserviceApp::class.java, *args)
}

@Configuration
class Configuration(
  private val builder: WebClient.Builder,
) {
  @Bean
  fun webClient(): WebClient = builder.build()
}

@RestController
class Controller(
  private val webClient: WebClient,
  @Value("\${server.host:localhost}") private val serverHost: String,
) {
  private val log: Logger = LoggerFactory.getLogger(Controller::class.java)

  @GetMapping("/test-call")
  fun call(): Mono<String> {
    return webClient.post().uri("http://$serverHost:7778/order")
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .capturedBody(CreateOrderRequest(productId = 10L, timestamp = LocalDateTime.now()))
      .retrieve()
      .bodyToMono(String::class.java)
      .doOnNext { log.info("Received order creation response=$it") }
  }
}

data class CreateOrderRequest(
  val productId: Long,
  val timestamp: LocalDateTime,
)