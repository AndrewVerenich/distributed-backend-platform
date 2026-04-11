package com.andver.bff.admin.client

import com.andver.bff.admin.backend.CreateProductBackendRequest
import com.andver.bff.admin.backend.ProductBackendDto
import com.andver.bff.admin.backend.ProductStatsBackendDto
import com.andver.bff.admin.backend.UpdateProductBackendRequest
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

  fun stats(correlationId: String): Mono<ProductStatsBackendDto> =
    productWebClient.get()
      .uri("/api/products/stats")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<ProductStatsBackendDto>()

  fun create(req: CreateProductBackendRequest, correlationId: String): Mono<ProductBackendDto> =
    productWebClient.post()
      .uri("/api/products")
      .header(CORRELATION_HEADER, correlationId)
      .bodyValue(req)
      .retrieve()
      .bodyToMono<ProductBackendDto>()

  fun update(id: Long, req: UpdateProductBackendRequest, correlationId: String): Mono<ProductBackendDto> =
    productWebClient.put()
      .uri("/api/products/{id}", id)
      .header(CORRELATION_HEADER, correlationId)
      .bodyValue(req)
      .retrieve()
      .bodyToMono<ProductBackendDto>()

  fun bulk(items: List<CreateProductBackendRequest>, correlationId: String): Flux<ProductBackendDto> =
    productWebClient.post()
      .uri("/api/products/bulk")
      .header(CORRELATION_HEADER, correlationId)
      .bodyValue(items)
      .retrieve()
      .bodyToFlux<ProductBackendDto>()

  companion object {
    private const val CORRELATION_HEADER = "X-Correlation-Id"
  }
}
