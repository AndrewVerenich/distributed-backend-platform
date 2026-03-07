package com.andver.gateway.filter

import com.andver.gateway.properties.RoutingProperties
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ProxyFilter(
  routingProperties: RoutingProperties,
  webClientBuilder: WebClient.Builder
) : WebFilter {
  private val log = LoggerFactory.getLogger(ProxyFilter::class.java)

  private val authClient = webClientBuilder
    .baseUrl(routingProperties.authService)
    .build()

  private val resourceClient = webClientBuilder
    .baseUrl(routingProperties.resourceService)
    .build()

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val request = exchange.request
    val path = request.path.value()

    if (path.startsWith("/actuator/")) {
      return chain.filter(exchange)
    }

    val targetClient = if (path.startsWith("/auth/")) authClient else resourceClient

    val serviceName = if (path.startsWith("/auth/")) "auth-service" else "resource-service"
    log.debug("Proxying request: ${request.method} $path to $serviceName")

    return ReactiveSecurityContextHolder.getContext()
      .defaultIfEmpty(SecurityContextImpl())
      .flatMap { securityContext ->
        val headers = HttpHeaders()
        headers.addAll(request.headers)

        val authentication = securityContext.authentication
        if (authentication != null && authentication.isAuthenticated) {
          val details = authentication.details as? Map<*, *>
          details?.let {
            headers.set("X-User-Id", authentication.principal.toString())
            headers.set("X-Username", it["username"]?.toString() ?: "")
          }
        }

        targetClient
          .method(request.method ?: HttpMethod.GET)
          .uri(path)
          .headers { it.addAll(headers) }
          .body(request.body, DataBuffer::class.java)
          .exchangeToMono { response ->
            exchange.response.statusCode = response.statusCode()
            exchange.response.headers.addAll(response.headers().asHttpHeaders())
            exchange.response.writeWith(response.bodyToFlux<DataBuffer>())
          }
      }
  }
}
