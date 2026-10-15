package com.andver.circuitbreaker.dependency

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

enum class PaymentMode {
  OK,
  ERROR,
  SLOW,
}

data class ControlRequest(
  val paymentMode: PaymentMode = PaymentMode.OK,
  val errorRate: Double = 1.0,
  val delayMs: Long = 3000,
)

data class ControlState(
  val paymentMode: PaymentMode = PaymentMode.OK,
  val errorRate: Double = 1.0,
  val delayMs: Long = 3000,
)

@Configuration
class DependencyRoutes {
  private val state = AtomicReference(ControlState())

  @Bean
  fun routes(): RouterFunction<ServerResponse> =
    router {
      accept(MediaType.APPLICATION_JSON).nest {
        GET("/payment") { pay() }
        GET("/catalog") { catalog() }
        GET("/control") {
          ServerResponse.ok().bodyValue(state.get())
        }
        POST("/control") { request ->
          request.bodyToMono(ControlRequest::class.java).flatMap { body ->
            state.set(
              ControlState(
                paymentMode = body.paymentMode,
                errorRate = body.errorRate.coerceIn(0.0, 1.0),
                delayMs = body.delayMs.coerceAtLeast(0),
              ),
            )
            ServerResponse.ok().bodyValue(state.get())
          }
        }
      }
    }

  private fun pay(): Mono<ServerResponse> {
    val current = state.get()
    return when (current.paymentMode) {
      PaymentMode.OK ->
        ServerResponse.ok().bodyValue(mapOf("status" to "paid"))

      PaymentMode.ERROR -> {
        val roll = ThreadLocalRandom.current().nextDouble()
        if (roll < current.errorRate) {
          ServerResponse.status(503).bodyValue(mapOf("status" to "payment_failed"))
        } else {
          ServerResponse.ok().bodyValue(mapOf("status" to "paid"))
        }
      }

      PaymentMode.SLOW ->
        Mono
          .delay(Duration.ofMillis(current.delayMs))
          .flatMap {
            ServerResponse.ok().bodyValue(mapOf("status" to "paid", "slow" to true))
          }
    }
  }

  private fun catalog(): Mono<ServerResponse> =
    ServerResponse.ok().bodyValue(
      mapOf(
        "items" to listOf("sku-1", "sku-2", "sku-3"),
        "status" to "ok",
      ),
    )
}
