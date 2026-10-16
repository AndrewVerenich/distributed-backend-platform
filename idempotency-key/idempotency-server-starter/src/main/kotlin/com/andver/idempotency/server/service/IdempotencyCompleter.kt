package com.andver.idempotency.server.service

import com.andver.idempotency.server.IdempotencyContext
import com.andver.idempotency.server.filter.IdempotencyWebFilter
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

class IdempotencyCompleter(
  private val claimService: IdempotencyClaimService,
) {

  fun complete(
    exchange: ServerWebExchange,
    status: Int,
    body: String,
    contentType: MediaType = MediaType.APPLICATION_JSON,
  ): Mono<Void> {
    val context = requireContext(exchange)
    return claimService.complete(
      context = context,
      responseStatus = status,
      responseContentType = contentType.toString(),
      responseBody = body.toByteArray(StandardCharsets.UTF_8),
    )
  }

  fun complete(
    context: IdempotencyContext,
    status: Int,
    body: String,
    contentType: MediaType = MediaType.APPLICATION_JSON,
  ): Mono<Void> =
    claimService.complete(
      context = context,
      responseStatus = status,
      responseContentType = contentType.toString(),
      responseBody = body.toByteArray(StandardCharsets.UTF_8),
    )

  fun requireContext(exchange: ServerWebExchange): IdempotencyContext =
    IdempotencyWebFilter.context(exchange)
      ?: error("Idempotency context missing; is the path covered by idempotency.server.paths?")
}
