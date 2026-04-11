package com.andver.bff.gateway.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class CorrelationIdGlobalFilter : GlobalFilter, Ordered {

  override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
    val request = exchange.request
    val correlationId = request.headers.getFirst(HEADER)?.takeIf { it.isNotBlank() }
      ?: UUID.randomUUID().toString()
    val mutatedRequest: ServerHttpRequest = request.mutate()
      .header(HEADER, correlationId)
      .build()
    exchange.response.headers.set(HEADER, correlationId)
    return chain.filter(exchange.mutate().request(mutatedRequest).build())
  }

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

  companion object {
    const val HEADER = "X-Correlation-Id"
  }
}
