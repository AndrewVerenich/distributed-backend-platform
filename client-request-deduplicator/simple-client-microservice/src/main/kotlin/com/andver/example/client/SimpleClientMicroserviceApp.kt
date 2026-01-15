package com.andver.example.client

import com.andver.clientdeduplicator.starter.inserter.capturedBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
class Controller(private val webClient: WebClient) {
  private val log: Logger = LoggerFactory.getLogger(Controller::class.java)

  @GetMapping("/call")
  fun call(): Mono<String> {
    return webClient.post().uri("http://localhost:7778/test")
      .capturedBody(Request("body", LocalDateTime.now()))
      .retrieve()
      .bodyToMono(String::class.java)
      .doOnNext { log.info("Received response=$it") }
  }
}

data class Request(
  val name: String,
  val timestamp: LocalDateTime,
)