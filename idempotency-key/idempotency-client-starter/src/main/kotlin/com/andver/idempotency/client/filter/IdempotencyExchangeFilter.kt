package com.andver.idempotency.client.filter

import com.andver.idempotency.api.IdempotencyHeaders
import com.andver.idempotency.client.IdempotencyClientAttributes
import com.andver.idempotency.client.IdempotencyClientProperties
import com.andver.idempotency.client.metrics.IdempotencyClientMetrics
import com.andver.idempotency.client.store.OperationKeyStore
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class IdempotencyExchangeFilter(
  private val keyStore: OperationKeyStore,
  private val properties: IdempotencyClientProperties,
  private val metrics: IdempotencyClientMetrics,
) : ExchangeFilterFunction {

  override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
    if (!properties.enabled) return next.exchange(request)

    val operationId = request.attribute(IdempotencyClientAttributes.OPERATION_ID)
      .map { it as String }
      .orElse(null)
      ?: return next.exchange(request)
    val owner = request.attribute(IdempotencyClientAttributes.OWNER)
      .map { it as String }
      .orElse(null)
      ?: error("idempotency.owner attribute is required when operationId is set")

    val key = keyStore.resolve(operationId)
    val delayHolder = AtomicReference(properties.defaultRetryDelay)

    return Mono.defer {
      val enriched = ClientRequest.from(request)
        .headers { headers ->
          headers.set(IdempotencyHeaders.KEY, key)
          headers.set(IdempotencyHeaders.OWNER, owner)
          headers.set(IdempotencyHeaders.OPERATION_ID, operationId)
        }
        .build()
      next.exchange(enriched)
        .flatMap { response ->
          when {
            response.statusCode() == HttpStatus.CONFLICT -> {
              val retryAfter = parseRetryAfter(response) ?: properties.defaultRetryDelay
              delayHolder.set(retryAfter)
              metrics.retry("conflict")
              response.releaseBody().then(Mono.error(RetryableIdempotencyException("conflict")))
            }
            response.statusCode() == HttpStatus.UNPROCESSABLE_ENTITY -> {
              metrics.stopUnprocessable()
              Mono.just(response)
            }
            else -> Mono.just(response)
          }
        }
    }.retryWhen(
      Retry.from { signals ->
        signals.concatMap { signal ->
          val failure = signal.failure()
          val attempt = signal.totalRetries() + 1
          if (attempt > properties.maxRetries || !isRetryable(failure)) {
            Mono.error(failure)
          } else {
            if (failure !is RetryableIdempotencyException) {
              metrics.retry("transport")
            }
            Mono.delay(delayHolder.get())
          }
        }
      },
    )
  }

  private fun isRetryable(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
      val name = current.javaClass.name
      if (current is RetryableIdempotencyException ||
        current is TimeoutException ||
        current is WebClientRequestException ||
        name.endsWith("ReadTimeoutException") ||
        name.endsWith("TimeoutException")
      ) {
        return true
      }
      current = current.cause
    }
    return false
  }

  private fun parseRetryAfter(response: ClientResponse): Duration? {
    val raw = response.headers().asHttpHeaders().getFirst("Retry-After") ?: return null
    val seconds = raw.toLongOrNull() ?: return null
    return Duration.ofSeconds(seconds.coerceAtLeast(1))
  }

  class RetryableIdempotencyException(message: String) : RuntimeException(message)
}
