package com.andver.bff.gateway.ratelimit

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Mono

@Configuration
class RateLimitConfig {

  /** One KeyResolver bean; keys include route id so web/mobile/admin keep separate Redis buckets. */
  @Bean
  fun routeAwareRateLimitKeyResolver(): KeyResolver =
    KeyResolver { exchange ->
      val route: Route? = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
      val id = route?.id ?: "unknown"
      Mono.just("rl:$id")
    }
}
