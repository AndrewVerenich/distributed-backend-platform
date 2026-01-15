package com.andver.example.client

import com.andver.clientdeduplicator.starter.filter.WebClientDeduplicationFilter
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@SpringBootApplication
class SimpleClientMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleClientMicroserviceApp::class.java, *args)
}

@Configuration
class Configuration(
  private val builder: WebClient.Builder,
  private val deduplicationFilter: WebClientDeduplicationFilter
) {

  @Bean
  fun webClient(): WebClient = builder.filter(deduplicationFilter).build()
}

@RestController
class Controller(private val webClient: WebClient) {
  @GetMapping("/call")
  fun call(): Mono<String> {
    return webClient.post().uri("http://localhost:7778/test")
      .bodyValue("body")
      .retrieve()
      .bodyToMono(String::class.java)
  }
}