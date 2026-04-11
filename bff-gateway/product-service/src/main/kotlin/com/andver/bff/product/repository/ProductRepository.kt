package com.andver.bff.product.repository

import com.andver.bff.product.entity.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface ProductRepository : ReactiveCrudRepository<Product, Long> {

  fun findByIsActiveTrue(pageable: Pageable): Flux<Product>

  fun findTop10ByIsActiveTrueOrderBySalesCountDesc(): Flux<Product>

  fun findByIdGreaterThanAndIsActiveTrue(id: Long, pageable: Pageable): Flux<Product>

  fun countByIsActiveTrue(): Mono<Long>

  @Query("SELECT COALESCE(SUM(sales_count), 0) FROM product WHERE is_active = true")
  fun sumSalesCountActive(): Mono<Long>
}
