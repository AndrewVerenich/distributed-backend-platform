package com.andver.clientratelimit.upstream

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@Configuration
class UpstreamRoutes(
  private val limiter: InMemoryFixedWindowLimiter,
) {
  @Bean
  fun router(): RouterFunction<ServerResponse> = router {
    GET("/api/catalog", ::catalog)
    GET("/api/catalog/**", ::catalog)
    GET("/api/checkout", ::checkout)
    GET("/api/checkout/**", ::checkout)
  }

  private fun catalog(request: ServerRequest): Mono<ServerResponse> = handle(request)

  private fun checkout(request: ServerRequest): Mono<ServerResponse> = handle(request)

  private fun handle(request: ServerRequest): Mono<ServerResponse> {
    val decision = limiter.check(request.path())
    val status = if (decision.allowed) HttpStatus.OK else HttpStatus.TOO_MANY_REQUESTS
    var builder = ServerResponse.status(status)
      .contentType(MediaType.APPLICATION_JSON)
      .header("RateLimit-Limit", decision.limit.toString())
      .header("RateLimit-Remaining", decision.remaining.toString())
      .header("RateLimit-Reset", decision.resetDeltaSeconds.toString())
      .header("X-RateLimit-Limit", decision.limit.toString())
      .header("X-RateLimit-Remaining", decision.remaining.toString())
      .header("X-RateLimit-Reset", decision.resetEpochSeconds.toString())
      .header("X-RateLimit-Policy", decision.rule)
    if (!decision.allowed) {
      builder = builder.header("Retry-After", decision.resetDeltaSeconds.coerceAtLeast(1).toString())
    }
    return builder.bodyValue(
      mapOf(
        "ok" to decision.allowed,
        "rule" to decision.rule,
        "path" to request.path(),
        "limit" to decision.limit,
        "remaining" to decision.remaining,
        "resetAfterSec" to decision.resetDeltaSeconds,
      ),
    )
  }
}
