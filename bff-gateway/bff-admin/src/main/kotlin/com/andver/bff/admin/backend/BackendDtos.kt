package com.andver.bff.admin.backend

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

data class UserStatsBackendDto(
  val totalUsers: Long,
  val activeUsers: Long,
)

data class CreateUserBackendRequest(
  val email: String,
  val name: String,
  val createdBy: String? = null,
)

data class UpdateUserBackendRequest(
  val email: String? = null,
  val name: String? = null,
  val isActive: Boolean? = null,
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

data class ProductStatsBackendDto(
  val totalProducts: Long,
  val activeProducts: Long,
  val totalSalesUnits: Long,
)

data class CreateProductBackendRequest(
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

data class UpdateProductBackendRequest(
  val name: String? = null,
  val description: String? = null,
  val price: BigDecimal? = null,
  val category: String? = null,
  val images: String? = null,
  val isActive: Boolean? = null,
)
