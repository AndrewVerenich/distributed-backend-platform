package com.andver.bff.web.backend

import java.math.BigDecimal
import java.time.Instant

data class ProductBackendDto(
  val id: Long,
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: String?,
  val rating: BigDecimal,
  val reviewCount: Int,
  val salesCount: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

data class ProductPageBackendDto(
  val content: List<ProductBackendDto>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
)

data class ProductStatsBackendDto(
  val totalProducts: Long,
  val activeProducts: Long,
  val totalSalesUnits: Long,
)
