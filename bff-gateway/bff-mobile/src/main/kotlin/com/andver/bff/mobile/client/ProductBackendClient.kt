package com.andver.bff.mobile.client

import com.andver.bff.mobile.backend.CursorPageBackendDto
import com.andver.bff.mobile.backend.ProductBackendDto
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class ProductBackendClient(
  private val productWebClient: WebClient,
) {

  fun getProduct(id: Long, correlationId: String): Mono<ProductBackendDto> =
    productWebClient.get()
      .uri("/api/products/{id}", id)
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<ProductBackendDto>()

  fun cursor(afterId: Long, limit: Int, correlationId: String): Mono<CursorPageBackendDto> =
    productWebClient.get()
      .uri { b ->
        b.path("/api/products/cursor")
          .queryParam("afterId", afterId)
          .queryParam("limit", limit)
          .build()
      }
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<CursorPageBackendDto>()

  fun popular(correlationId: String): Flux<ProductBackendDto> =
    productWebClient.get()
      .uri("/api/products/popular")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToFlux<ProductBackendDto>()

  companion object {
    private const val CORRELATION_HEADER = "X-Correlation-Id"
  }
}
