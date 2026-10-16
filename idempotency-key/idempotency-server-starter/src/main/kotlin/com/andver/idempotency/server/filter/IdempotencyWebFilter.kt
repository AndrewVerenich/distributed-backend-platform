package com.andver.idempotency.server.filter

import com.andver.idempotency.api.IdempotencyHeaders
import com.andver.idempotency.server.IdempotencyContext
import com.andver.idempotency.server.IdempotencyServerProperties
import com.andver.idempotency.server.metrics.IdempotencyServerMetrics
import com.andver.idempotency.server.owner.OwnerResolver
import com.andver.idempotency.server.repository.IdempotencyRecord
import com.andver.idempotency.server.service.ClaimResult
import com.andver.idempotency.server.service.IdempotencyClaimService
import org.springframework.core.Ordered
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class IdempotencyWebFilter(
  private val properties: IdempotencyServerProperties,
  private val claimService: IdempotencyClaimService,
  private val ownerResolver: OwnerResolver,
  private val metrics: IdempotencyServerMetrics,
) : WebFilter, Ordered {

  private val pathMatcher = PathMatcher(properties.paths)

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 30

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    if (!properties.enabled) return chain.filter(exchange)
    val path = exchange.request.path.value()
    if (!pathMatcher.matches(path)) return chain.filter(exchange)
    if (exchange.request.method != HttpMethod.POST &&
      exchange.request.method != HttpMethod.PUT &&
      exchange.request.method != HttpMethod.PATCH
    ) {
      return chain.filter(exchange)
    }

    val key = exchange.request.headers.getFirst(IdempotencyHeaders.KEY)?.trim()
    if (key.isNullOrBlank() || key.length > 255) {
      metrics.missingKey()
      return writeJson(exchange, HttpStatus.BAD_REQUEST, """{"error":"missing or invalid Idempotency-Key"}""")
    }
    val owner = ownerResolver.resolve(exchange.request)
    if (owner.isNullOrBlank()) {
      metrics.missingOwner()
      return writeJson(exchange, HttpStatus.BAD_REQUEST, """{"error":"missing Idempotency-Key-Owner"}""")
    }

    return DataBufferUtils.join(exchange.request.body)
      .defaultIfEmpty(exchange.response.bufferFactory().wrap(ByteArray(0)))
      .flatMap { dataBuffer ->
        val bytes = ByteArray(dataBuffer.readableByteCount())
        dataBuffer.read(bytes)
        DataBufferUtils.release(dataBuffer)
        if (bytes.size > properties.maxBodyBytes) {
          return@flatMap writeJson(
            exchange,
            HttpStatus.PAYLOAD_TOO_LARGE,
            """{"error":"request body exceeds idempotency maxBodyBytes"}""",
          )
        }
        val bodyHash = BodyHasher.sha256(bytes)
        val decorated = decorate(exchange, bytes)
        claimService.claim(owner, key, bodyHash)
          .flatMap { result -> handleClaim(decorated, chain, result) }
      }
  }

  private fun handleClaim(
    exchange: ServerWebExchange,
    chain: WebFilterChain,
    result: ClaimResult,
  ): Mono<Void> =
    when (result) {
      is ClaimResult.Proceed -> {
        exchange.attributes[CONTEXT] = result.context
        chain.filter(exchange)
      }
      is ClaimResult.Replay -> writeReplay(exchange, result.record)
      is ClaimResult.InProgress -> {
        val seconds = result.retryAfter.seconds.coerceAtLeast(1)
        exchange.response.headers.set(RETRY_AFTER, seconds.toString())
        writeJson(
          exchange,
          HttpStatus.CONFLICT,
          """{"error":"idempotency key in progress","retryAfterSeconds":$seconds}""",
        )
      }
      ClaimResult.BodyConflict -> writeJson(
        exchange,
        HttpStatus.UNPROCESSABLE_ENTITY,
        """{"error":"idempotency key reused with a different request body"}""",
      )
    }

  private fun writeReplay(exchange: ServerWebExchange, record: IdempotencyRecord): Mono<Void> {
    val response = exchange.response
    response.statusCode = HttpStatus.valueOf(record.responseStatus ?: 200)
    record.responseContentType?.let { response.headers.contentType = MediaType.parseMediaType(it) }
    response.headers.set(IdempotencyHeaders.REPLAYED, "true")
    val body = record.responseBody ?: ByteArray(0)
    val buffer = response.bufferFactory().wrap(body)
    return response.writeWith(Mono.just(buffer))
  }

  private fun writeJson(exchange: ServerWebExchange, status: HttpStatus, payload: String): Mono<Void> {
    val response = exchange.response
    response.statusCode = status
    response.headers.contentType = MediaType.APPLICATION_JSON
    val buffer = response.bufferFactory().wrap(payload.toByteArray())
    return response.writeWith(Mono.just(buffer))
  }

  private fun decorate(exchange: ServerWebExchange, bytes: ByteArray): ServerWebExchange {
    val decoratedRequest = object : ServerHttpRequestDecorator(exchange.request) {
      override fun getBody(): Flux<DataBuffer> =
        Flux.defer {
          Flux.just(exchange.response.bufferFactory().wrap(bytes))
        }
    }
    return exchange.mutate().request(decoratedRequest).build()
  }

  companion object {
    const val CONTEXT = "idempotency.context"
    private const val RETRY_AFTER = "Retry-After"

    fun context(exchange: ServerWebExchange): IdempotencyContext? =
      exchange.getAttribute(CONTEXT)
  }
}
