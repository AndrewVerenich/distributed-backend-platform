package com.andver.ratelimit.filter

import com.andver.ratelimit.RateLimiterProperties
import com.andver.ratelimit.key.RateLimitKeyResolver
import com.andver.ratelimit.metrics.RateLimitMetrics
import com.andver.ratelimit.model.FailureMode
import com.andver.ratelimit.model.RateLimitDecision
import com.andver.ratelimit.redis.RateLimiter
import com.andver.ratelimit.redis.ShardAwareException
import com.andver.ratelimit.rules.RateLimitRuleSet
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class RateLimitWebFilter(
  private val limiter: RateLimiter,
  private val rules: RateLimitRuleSet,
  private val keys: RateLimitKeyResolver,
  private val metrics: RateLimitMetrics,
  private val properties: RateLimiterProperties,
) : WebFilter, Ordered {

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 20

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val path = exchange.request.path.value()
    val rule = rules.match(path) ?: return chain.filter(exchange)
    val identity = keys.resolve(exchange.request, rule)
    return limiter.check(rule, identity)
      .onErrorResume { error ->
        val shard = (error as? ShardAwareException)?.shard ?: properties.sharding.singleShardName
        log.debug("rate limiter redis failure rule={} identity={} shard={}", rule.name, identity, shard, error)
        metrics.redisError(rule.name, rule.algorithm, shard)
        val allow = properties.onRedisError == FailureMode.ALLOW
        Mono.just(RateLimitDecision.unavailable(rule, allow, shard))
      }
      .flatMap { decision ->
        exchange.attributes[DECISION] = decision
        RateLimitHeaders.write(exchange.response.headers, decision)
        if (decision.allowed) {
          chain.filter(exchange)
        } else {
          reject(exchange, decision)
        }
      }
  }

  private fun reject(exchange: ServerWebExchange, decision: RateLimitDecision): Mono<Void> {
    val response = exchange.response
    response.statusCode = HttpStatus.TOO_MANY_REQUESTS
    response.headers.contentType = MediaType.APPLICATION_JSON
    val error = if (decision.unavailable) "rate limiter unavailable" else "rate limit exceeded"
    val payload = buildString {
      append("{\"error\":\"").append(error)
      append("\",\"rule\":\"").append(escape(decision.rule))
      append("\",\"algorithm\":\"").append(decision.algorithm.configName())
      append("\",\"shard\":\"").append(escape(decision.shard))
      append("\",\"limit\":").append(decision.limit)
      append(",\"remaining\":").append(decision.remaining)
      append(",\"retryAfterMs\":").append(decision.retryAfter.toMillis())
      append('}')
    }
    val body = response.bufferFactory().wrap(payload.toByteArray(Charsets.UTF_8))
    return response.writeWith(Mono.just(body))
  }

  private fun escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  companion object {
    private val log = LoggerFactory.getLogger(RateLimitWebFilter::class.java)
    const val DECISION = "rateLimiter.decision"

    fun decision(exchange: ServerWebExchange): RateLimitDecision? =
      exchange.getAttribute(DECISION)
  }
}
