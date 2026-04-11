package com.andver.bff.product.model

import java.math.BigDecimal
import java.time.Instant

data class ProductResponse(
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

data class ProductPageResponse(
  val content: List<ProductResponse>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
)

data class ProductStatsResponse(
  val totalProducts: Long,
  val activeProducts: Long,
  val totalSalesUnits: Long,
)

data class CreateProductRequest(
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: String?,
  val rating: BigDecimal? = null,
  val reviewCount: Int? = null,
  val salesCount: Int? = null,
  val createdBy: String? = null,
)

data class UpdateProductRequest(
  val name: String? = null,
  val description: String? = null,
  val price: BigDecimal? = null,
  val category: String? = null,
  val images: String? = null,
  val isActive: Boolean? = null,
)
