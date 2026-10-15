package com.andver.circuitbreaker.dumb

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Configuration
class DumbRoutes(
  private val dependencyWebClient: WebClient,
  private val demoTimers: DemoTimers,
) {
  private val checkoutInFlight = AtomicInteger()
  private val browseInFlight = AtomicInteger()

  @Bean
  fun routes(): RouterFunction<ServerResponse> =
    router {
      accept(MediaType.APPLICATION_JSON).nest {
        GET("/checkout") { checkout() }
        GET("/browse") { browse() }
        GET("/debug/inflight") {
          ServerResponse.ok().bodyValue(
            mapOf(
              "checkout" to checkoutInFlight.get(),
              "browse" to browseInFlight.get(),
            ),
          )
        }
      }
    }

  private fun checkout(): Mono<ServerResponse> {
    val start = System.nanoTime()
    checkoutInFlight.incrementAndGet()
    return dependencyWebClient
      .get()
      .uri("/payment")
      .retrieve()
      .bodyToMono(Map::class.java)
      .flatMap { body ->
        ServerResponse.ok().bodyValue(mapOf("client" to "dumb", "payment" to body))
      }.onErrorResume { ex ->
        ServerResponse.status(502).bodyValue(mapOf("client" to "dumb", "error" to (ex.message ?: "upstream")))
      }.doFinally {
        checkoutInFlight.decrementAndGet()
        demoTimers.checkout.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
      }
  }

  private fun browse(): Mono<ServerResponse> {
    val start = System.nanoTime()
    browseInFlight.incrementAndGet()
    return dependencyWebClient
      .get()
      .uri("/catalog")
      .retrieve()
      .bodyToMono(Map::class.java)
      .flatMap { body ->
        ServerResponse.ok().bodyValue(mapOf("client" to "dumb", "catalog" to body))
      }.onErrorResume { ex ->
        ServerResponse.status(502).bodyValue(mapOf("client" to "dumb", "error" to (ex.message ?: "upstream")))
      }.doFinally {
        browseInFlight.decrementAndGet()
        demoTimers.browse.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
      }
  }
}
