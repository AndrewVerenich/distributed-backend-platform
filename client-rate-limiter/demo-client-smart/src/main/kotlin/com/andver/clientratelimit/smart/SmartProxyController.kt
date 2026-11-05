package com.andver.clientratelimit.smart

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

@RestController
class SmartProxyController(
  private val webClient: WebClient,
) {
  @GetMapping("/proxy/catalog")
  fun catalog(): Mono<ResponseEntity<String>> = call("/api/catalog")

  @GetMapping("/proxy/checkout")
  fun checkout(): Mono<ResponseEntity<String>> = call("/api/checkout")

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

  private fun call(path: String): Mono<ResponseEntity<String>> =
    webClient.get()
      .uri(path)
      .exchangeToMono { response ->
        response.bodyToMono(String::class.java).defaultIfEmpty("").map { body ->
          ResponseEntity.status(response.statusCode()).body(body)
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
