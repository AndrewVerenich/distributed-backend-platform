package com.andver.bff.mobile.backend

import java.math.BigDecimal
import java.time.Instant

data class UserBackendDto(
  val id: Long,
  val email: String,
  val name: String,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

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

data class CursorPageBackendDto(
  val items: List<ProductBackendDto>,
  val nextCursor: String?,
)
