package com.andver.singleflight.demo.api

import com.andver.singleflight.Singleflight
import com.andver.singleflight.SingleflightProperties
import com.andver.singleflight.demo.model.Product
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@RestController
class ProductController(
  private val singleflight: Singleflight,
  private val properties: SingleflightProperties,
  private val upstreamWebClient: WebClient,
) {

  @GetMapping("/products/{id}")
  fun get(@PathVariable id: String): Mono<ResponseEntity<Product>> =
    singleflight.execute(id, Product::class.java) {
      upstreamWebClient.get()
        .uri("/products/{id}", id)
        .retrieve()
        .bodyToMono(Product::class.java)
    }.map { outcome ->
      ResponseEntity.ok()
        .header("X-Singleflight-Role", outcome.role.name)
        .header("X-Singleflight-Mode", properties.mode.name)
        .body(outcome.value)
    }
}
