package com.andver.bff.gateway.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class RequestLoggingGlobalFilter : GlobalFilter, Ordered {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
    val req = exchange.request
    val cid = req.headers.getFirst(CorrelationIdGlobalFilter.HEADER) ?: "-"
    log.info(
      "gateway request method={} path={} correlationId={}",
      req.method,
      req.path.value(),
      cid,
    )
    return chain.filter(exchange)
  }

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1
}
