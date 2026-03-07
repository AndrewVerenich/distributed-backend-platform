package com.andver.gateway.filter

import com.andver.gateway.converter.JwtAuthenticationConverter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(
  private val authenticationConverter: JwtAuthenticationConverter,
  private val meterRegistry: MeterRegistry
) : WebFilter {
  private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val path = exchange.request.path.value()

    if (path.startsWith("/auth/") || path.startsWith("/actuator/")) {
      return chain.filter(exchange)
    }

    return authenticationConverter.convert(exchange)
      .flatMap { authentication ->
        meterRegistry.counter("gateway.jwt.success").increment()
        chain.filter(exchange).contextWrite(
          ReactiveSecurityContextHolder.withAuthentication(authentication)
        )
      }
      .switchIfEmpty(
        Mono.defer {
          meterRegistry.counter("gateway.jwt.failure").increment()
          log.warn("Authentication failed for path: $path")
          exchange.response.statusCode = HttpStatus.UNAUTHORIZED
          exchange.response.setComplete()
        }
      )
  }
}
