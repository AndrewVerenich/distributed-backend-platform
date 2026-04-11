package com.andver.bff.admin.model

import java.math.BigDecimal
import java.time.Instant

data class AdminUserResponse(
  val id: Long,
  val email: String,
  val name: String,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

data class AdminUserPageResponse(
  val content: List<AdminUserResponse>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
)

data class AdminProductResponse(
  val id: Long,
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: List<String>,
  val rating: BigDecimal,
  val reviewCount: Int,
  val salesCount: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

data class AdminDashboardResponse(
  val users: UserMetrics,
  val products: ProductMetrics,
)

data class UserMetrics(
  val total: Long,
  val active: Long,
)

data class ProductMetrics(
  val total: Long,
  val active: Long,
  val salesUnits: Long,
)

data class AdminUpdateUserRequest(
  val email: String? = null,
  val name: String? = null,
  val isActive: Boolean? = null,
)

data class AdminCreateProductRequest(
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: String?,
)

data class AdminUpdateProductRequest(
  val name: String? = null,
  val description: String? = null,
  val price: BigDecimal? = null,
  val category: String? = null,
  val images: String? = null,
  val isActive: Boolean? = null,
)
