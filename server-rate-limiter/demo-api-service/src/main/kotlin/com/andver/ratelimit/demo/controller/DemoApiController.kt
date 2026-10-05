package com.andver.ratelimit.demo.controller

import com.andver.ratelimit.filter.RateLimitWebFilter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange

@RestController
class DemoApiController {

  @GetMapping("/api/catalog/{sku}")
  fun catalog(@PathVariable sku: String, exchange: ServerWebExchange): Map<String, Any?> =
    quota(exchange) + mapOf("sku" to sku)

  @PostMapping("/api/checkout")
  fun checkout(exchange: ServerWebExchange): Map<String, Any?> =
    quota(exchange) + mapOf("status" to "accepted")

  @GetMapping("/api/search")
  fun search(@RequestParam q: String, exchange: ServerWebExchange): Map<String, Any?> =
    quota(exchange) + mapOf("q" to q)

  private fun quota(exchange: ServerWebExchange): Map<String, Any?> {
    val decision = RateLimitWebFilter.decision(exchange)
    return mapOf(
      "rule" to decision?.rule,
      "algorithm" to decision?.algorithm?.configName(),
      "limit" to decision?.limit,
      "remaining" to decision?.remaining,
    )
  }
}
