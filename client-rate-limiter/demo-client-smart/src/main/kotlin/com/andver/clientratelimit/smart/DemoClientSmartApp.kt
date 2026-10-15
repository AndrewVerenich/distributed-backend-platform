package com.andver.clientratelimit.smart

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
class DemoClientSmartApp

fun main(args: Array<String>) {
  runApplication<DemoClientSmartApp>(*args)
}

@Configuration
class WebClientConfig {
  @Bean
  fun webClient(builder: WebClient.Builder, @Value("\${upstream.base-url}") baseUrl: String): WebClient =
    builder.baseUrl(baseUrl).build()
}

@RestController
class SmartProxyController(
  private val webClient: WebClient,
) {
  @GetMapping("/proxy/catalog")
  fun catalog(): Mono<Map<String, Any?>> = call("/api/catalog")

  @GetMapping("/proxy/checkout")
  fun checkout(): Mono<Map<String, Any?>> = call("/api/checkout")

  @PostMapping("/load/catalog")
  fun loadCatalog(
    @RequestParam(defaultValue = "40") requests: Int,
    @RequestParam(defaultValue = "20") concurrency: Int,
  ): Mono<Map<String, Any>> = load("/api/catalog", requests, concurrency)

  @PostMapping("/load/checkout")
  fun loadCheckout(
    @RequestParam(defaultValue = "40") requests: Int,
    @RequestParam(defaultValue = "20") concurrency: Int,
  ): Mono<Map<String, Any>> = load("/api/checkout", requests, concurrency)

  private fun call(path: String): Mono<Map<String, Any?>> =
    webClient.get()
      .uri(path)
      .exchangeToMono { response ->
        response.bodyToMono(String::class.java).defaultIfEmpty("").map { body ->
          mapOf(
            "status" to response.statusCode().value(),
            "rateLimitReset" to response.headers().asHttpHeaders().getFirst("RateLimit-Reset"),
            "remaining" to response.headers().asHttpHeaders().getFirst("RateLimit-Remaining"),
            "body" to body,
          )
        }
      }

  private fun load(path: String, requests: Int, concurrency: Int): Mono<Map<String, Any>> {
    val ok = AtomicInteger()
    val limited = AtomicInteger()
    val errors = AtomicInteger()
    return Flux.range(1, requests.coerceAtLeast(1))
      .flatMap({
        webClient.get()
          .uri(path)
          .exchangeToMono { response ->
            response.releaseBody().then(
              Mono.fromCallable {
                when (response.statusCode().value()) {
                  200 -> ok.incrementAndGet()
                  429 -> limited.incrementAndGet()
                  else -> errors.incrementAndGet()
                }
              },
            )
          }
          .onErrorResume {
            errors.incrementAndGet()
            Mono.empty()
          }
      }, concurrency.coerceAtLeast(1))
      .then(
        Mono.fromCallable {
          mapOf(
            "client" to "smart",
            "path" to path,
            "requests" to requests,
            "concurrency" to concurrency,
            "ok" to ok.get(),
            "limited" to limited.get(),
            "errors" to errors.get(),
          )
        },
      )
  }
}
