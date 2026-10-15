package com.andver.circuitbreaker.filter

import com.andver.circuitbreaker.core.AcquireResult
import com.andver.circuitbreaker.core.CircuitBreakerOpenException
import com.andver.circuitbreaker.core.CircuitBreakerRegistry
import com.andver.circuitbreaker.core.NanoClock
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

/**
 * Isolates outbound calls by an explicit command name on [CircuitBreakerRequest.COMMAND_ATTRIBUTE].
 * Requests without the attribute pass through unchanged.
 */
class WebClientCircuitBreakerFilter(
  private val registry: CircuitBreakerRegistry,
  private val clock: NanoClock = NanoClock.SYSTEM,
) : ExchangeFilterFunction {
  override fun filter(
    request: ClientRequest,
    next: ExchangeFunction,
  ): Mono<ClientResponse> {
    val command =
      request
        .attribute(CircuitBreakerRequest.COMMAND_ATTRIBUTE)
        .map { it as String }
        .orElse(null)
        ?: return next.exchange(request)

    return when (val acquired = registry.breaker(command).tryAcquire()) {
      AcquireResult.Rejected -> Mono.error(CircuitBreakerOpenException(command))
      is AcquireResult.Permitted -> {
        val start = clock.nanoTime()
        next
          .exchange(request)
          .doOnNext { response ->
            acquired.recordHttpStatus(response.statusCode().value(), clock.nanoTime() - start)
          }.doOnError {
            acquired.recordError(clock.nanoTime() - start)
          }.doOnCancel {
            acquired.release()
          }
      }
    }
  }
}
