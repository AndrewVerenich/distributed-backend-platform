package com.andver.waitingroom.checkout.controller

import com.andver.waitingroom.checkout.config.CheckoutProperties
import com.andver.waitingroom.checkout.metrics.CheckoutMetrics
import com.andver.waitingroom.filter.TicketWebFilter
import com.andver.waitingroom.model.TicketPayload
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Semaphore

@RestController
class CheckoutController(
  private val properties: CheckoutProperties,
  private val metrics: CheckoutMetrics,
) {
  private val semaphore = Semaphore(properties.maxConcurrency)

  @PostMapping("/checkout")
  fun checkout(exchange: ServerWebExchange): Mono<ResponseEntity<Map<String, Any>>> {
    val ticket: TicketPayload = TicketWebFilter.payload(exchange)
      ?: run {
        metrics.error("missing_ticket")
        return Mono.just(
          ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("error" to "ticket required")),
        )
      }

    val acquired = semaphore.tryAcquire()
    if (!acquired) {
      metrics.error("saturated")
      return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
            mapOf(
              "error" to "checkout saturated",
              "maxConcurrency" to properties.maxConcurrency,
            ),
          ),
      )
    }

    val started = Instant.now()
    return Mono.delay(properties.latency)
      .publishOn(Schedulers.boundedElastic())
      .map {
        val latency = Duration.between(started, Instant.now())
        metrics.recordLatency(latency)
        metrics.success()
        ResponseEntity.ok(
          mapOf(
            "status" to "ok",
            "eventId" to ticket.eventId,
            "visitorId" to ticket.visitorId,
            "ticketId" to ticket.ticketId,
            "processedInMs" to latency.toMillis(),
          ) as Map<String, Any>,
        )
      }
      .doFinally { semaphore.release() }
      .onErrorResume { ex ->
        metrics.error("processing")
        Mono.just(
          ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to (ex.message ?: "checkout failed"))),
        )
      }
  }
}
