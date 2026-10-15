package com.andver.clientratelimit.dumb

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
class DemoClientDumbApp

fun main(args: Array<String>) {
  runApplication<DemoClientDumbApp>(*args)
}

@Configuration
class WebClientConfig {
  @Bean
  fun webClient(builder: WebClient.Builder, @Value("\${upstream.base-url}") baseUrl: String): WebClient =
    builder.baseUrl(baseUrl).build()
}

@RestController
class DumbProxyController(
  private val webClient: WebClient,
  private val meterRegistry: MeterRegistry,
  @Value("\${dumb-retry.fixed-delay:50ms}") private val fixedDelay: Duration,
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
    exchangeWithNaiveRetry(path).map { result ->
      mapOf(
        "status" to result.status,
        "retried" to result.retried,
        "body" to result.body,
      )
    }

  private fun load(path: String, requests: Int, concurrency: Int): Mono<Map<String, Any>> {
    val ok = AtomicInteger()
    val limited = AtomicInteger()
    val errors = AtomicInteger()
    val retries = AtomicInteger()
    return Flux.range(1, requests.coerceAtLeast(1))
      .flatMap({
        exchangeWithNaiveRetry(path)
          .doOnNext { result ->
            if (result.retried) {
              retries.incrementAndGet()
              meterRegistry.counter("crl_dumb_retries_total").increment()
            }
            when (result.status) {
              200 -> {
                ok.incrementAndGet()
                meterRegistry.counter("crl_dumb_requests_total", "result", "success").increment()
              }
              429 -> {
                limited.incrementAndGet()
                meterRegistry.counter("crl_dumb_requests_total", "result", "rate_limited").increment()
              }
              else -> {
                errors.incrementAndGet()
                meterRegistry.counter("crl_dumb_requests_total", "result", "error").increment()
              }
            }
          }
          .onErrorResume {
            errors.incrementAndGet()
            meterRegistry.counter("crl_dumb_requests_total", "result", "error").increment()
            Mono.empty()
          }
      }, concurrency.coerceAtLeast(1))
      .then(
        Mono.fromCallable {
          mapOf(
            "client" to "dumb",
            "path" to path,
            "requests" to requests,
            "concurrency" to concurrency,
            "ok" to ok.get(),
            "limited" to limited.get(),
            "errors" to errors.get(),
            "naiveRetries" to retries.get(),
            "fixedDelayMs" to fixedDelay.toMillis(),
          )
        },
      )
  }

  /**
   * Naive comparison client: on 429 waits a fixed delay (ignores RateLimit-* / X-RateLimit-*)
   * and retries once.
   */
  private fun exchangeWithNaiveRetry(path: String): Mono<CallResult> =
    webClient.get()
      .uri(path)
      .exchangeToMono { response ->
        response.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
          val status = response.statusCode().value()
          if (status != 429) {
            Mono.just(CallResult(status, body, retried = false))
          } else {
            Mono.delay(fixedDelay)
              .then(
                webClient.get()
                  .uri(path)
                  .exchangeToMono { retryResponse ->
                    retryResponse.bodyToMono(String::class.java).defaultIfEmpty("").map { retryBody ->
                      CallResult(retryResponse.statusCode().value(), retryBody, retried = true)
                    }
                  },
              )
          }
        }
      }

  private data class CallResult(
    val status: Int,
    val body: String,
    val retried: Boolean,
  )
}
