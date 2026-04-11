package com.andver.bff.web.client

import com.andver.bff.web.backend.ProductBackendDto
import com.andver.bff.web.backend.ProductPageBackendDto
import com.andver.bff.web.backend.ProductStatsBackendDto
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

  fun page(page: Int, size: Int, sort: String, desc: Boolean, correlationId: String): Mono<ProductPageBackendDto> =
    productWebClient.get()
      .uri { b ->
        b.path("/api/products")
          .queryParam("page", page)
          .queryParam("size", size)
          .queryParam("sort", sort)
          .queryParam("desc", desc)
          .build()
      }
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<ProductPageBackendDto>()

  fun popular(correlationId: String): Flux<ProductBackendDto> =
    productWebClient.get()
      .uri("/api/products/popular")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToFlux<ProductBackendDto>()

  fun stats(correlationId: String): Mono<ProductStatsBackendDto> =
    productWebClient.get()
      .uri("/api/products/stats")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<ProductStatsBackendDto>()

  companion object {
    private const val CORRELATION_HEADER = "X-Correlation-Id"
  }
}
