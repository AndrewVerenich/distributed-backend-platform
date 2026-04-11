package com.andver.bff.product.service

import com.andver.bff.product.entity.Product
import com.andver.bff.product.model.CreateProductRequest
import com.andver.bff.product.model.ProductPageResponse
import com.andver.bff.product.model.ProductResponse
import com.andver.bff.product.model.ProductStatsResponse
import com.andver.bff.product.model.UpdateProductRequest
import com.andver.bff.product.repository.ProductRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.kotlin.core.util.function.component3
import java.math.BigDecimal
import java.time.Instant

@Service
class ProductService(
  private val productRepository: ProductRepository,
) {

  fun findById(id: Long): Mono<ProductResponse> =
    productRepository.findById(id).map { it.toResponse() }

  fun listPaged(page: Int, size: Int, sortField: String, desc: Boolean): Mono<ProductPageResponse> {
    val field = if (sortField in ALLOWED_SORT) sortField else "name"
    val sort = if (desc) Sort.by(field).descending() else Sort.by(field).ascending()
    val pageable = PageRequest.of(page, size, sort)
    val content = productRepository.findByIsActiveTrue(pageable)
      .map { it.toResponse() }
      .collectList()
    val total = productRepository.countByIsActiveTrue()
    return Mono.zip(content, total) { list, t ->
      ProductPageResponse(content = list, page = page, size = size, totalElements = t)
    }
  }

  fun listCursor(afterId: Long, limit: Int): Mono<Pair<List<ProductResponse>, String?>> {
    val sort = Sort.by("id").ascending()
    val pageable = PageRequest.of(0, limit + 1, sort)
    val flux = if (afterId <= 0) {
      productRepository.findByIsActiveTrue(pageable)
    } else {
      productRepository.findByIdGreaterThanAndIsActiveTrue(afterId, pageable)
    }
    return flux.map { it.toResponse() }
      .collectList()
      .map { list ->
        val hasMore = list.size > limit
        val page = if (hasMore) list.dropLast(1) else list
        val nextCursor = if (hasMore && page.isNotEmpty()) page.last().id.toString() else null
        page to nextCursor
      }
  }

  fun popular(): Flux<ProductResponse> =
    productRepository.findTop10ByIsActiveTrueOrderBySalesCountDesc().map { it.toResponse() }

  fun stats(): Mono<ProductStatsResponse> {
    return Mono.zip(
      productRepository.count(),
      productRepository.countByIsActiveTrue(),
      productRepository.sumSalesCountActive(),
    ).map { (count, activeCount, totalSalesCount) ->
      ProductStatsResponse(
        totalProducts = count,
        activeProducts = activeCount,
        totalSalesUnits = totalSalesCount,
      )
    }
  }

  fun create(req: CreateProductRequest): Mono<ProductResponse> {
    val now = Instant.now()
    val product = Product(
      id = null,
      name = req.name,
      description = req.description,
      price = req.price,
      category = req.category,
      images = req.images,
      rating = req.rating ?: BigDecimal.ZERO,
      reviewCount = req.reviewCount ?: 0,
      salesCount = req.salesCount ?: 0,
      createdAt = now,
      updatedAt = now,
      createdBy = req.createdBy,
      isActive = true,
    )
    return productRepository.save(product).map { it.toResponse() }
  }

  fun update(id: Long, req: UpdateProductRequest): Mono<ProductResponse> {
    return productRepository.findById(id).flatMap { existing ->
      val updated = existing.copy(
        name = req.name ?: existing.name,
        description = req.description ?: existing.description,
        price = req.price ?: existing.price,
        category = req.category ?: existing.category,
        images = req.images ?: existing.images,
        isActive = req.isActive ?: existing.isActive,
        updatedAt = Instant.now(),
      )
      productRepository.save(updated).map { it.toResponse() }
    }
  }

  fun createAll(requests: List<CreateProductRequest>): Flux<ProductResponse> =
    Flux.fromIterable(requests).concatMap { create(it) }

  private fun Product.toResponse() = ProductResponse(
    id = id!!,
    name = name,
    description = description,
    price = price,
    category = category,
    images = images,
    rating = rating,
    reviewCount = reviewCount,
    salesCount = salesCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isActive = isActive,
  )

  companion object {
    private val ALLOWED_SORT = setOf("name", "price", "rating", "salesCount", "id", "createdAt")
  }
}
