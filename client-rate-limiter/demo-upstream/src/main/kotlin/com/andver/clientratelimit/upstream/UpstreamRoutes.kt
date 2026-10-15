package com.andver.clientratelimit.upstream

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

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

data class UpstreamDecision(
  val allowed: Boolean,
  val rule: String,
  val limit: Long,
  val remaining: Long,
  val resetDeltaSeconds: Long,
  val resetEpochSeconds: Long,
)

@Component
class InMemoryFixedWindowLimiter(
  private val properties: UpstreamRateLimitProperties,
  meterRegistry: MeterRegistry,
) {
  private val windows = ConcurrentHashMap<String, Window>()
  private val allowed = meterRegistry.counter("upstream_requests_total", "result", "allowed")
  private val rejected = meterRegistry.counter("upstream_requests_total", "result", "rejected")

  fun check(path: String): UpstreamDecision {
    val rule = match(path) ?: return UpstreamDecision(
      allowed = true,
      rule = "none",
      limit = Long.MAX_VALUE,
      remaining = Long.MAX_VALUE,
      resetDeltaSeconds = 0,
      resetEpochSeconds = System.currentTimeMillis() / 1000,
    )

    val windowMs = rule.window.toMillis().coerceAtLeast(1)
    val now = System.currentTimeMillis()
    val windowId = now / windowMs
    val key = "${rule.name}:$windowId"
    val window = windows.compute(key) { _, existing ->
      val current = existing ?: Window(AtomicLong(0))
      current.count.incrementAndGet()
      current
    }!!

    if (windows.size > 64) {
      windows.keys.filter { !it.endsWith(":$windowId") }.forEach { windows.remove(it) }
    }

    val count = window.count.get()
    val allowedReq = count <= rule.limit
    val remaining = (rule.limit - count).coerceAtLeast(0)
    val resetAtMs = (windowId + 1) * windowMs
    val resetDelta = ceil((resetAtMs - now).coerceAtLeast(0).toDouble() / 1000.0).toLong()
    if (allowedReq) allowed.increment() else rejected.increment()

    return UpstreamDecision(
      allowed = allowedReq,
      rule = rule.name,
      limit = rule.limit,
      remaining = remaining,
      resetDeltaSeconds = resetDelta,
      resetEpochSeconds = resetAtMs / 1000,
    )
  }

  private fun match(path: String): UpstreamRateLimitProperties.Rule? {
    var best: UpstreamRateLimitProperties.Rule? = null
    var bestLen = -1
    for (rule in properties.rules) {
      for (prefix in rule.pathPrefixes) {
        if (path.startsWith(prefix) && prefix.length > bestLen) {
          best = rule
          bestLen = prefix.length
        }
      }
    }
    return best
  }

  private data class Window(
    val count: AtomicLong,
  )
}
