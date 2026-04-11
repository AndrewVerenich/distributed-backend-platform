package com.andver.bff.web.model

import java.math.BigDecimal
import java.time.Instant

data class WebProfileResponse(
  val id: Long,
  val email: String,
  val name: String,
  val isActive: Boolean,
)

data class WebProductResponse(
  val id: Long,
  val name: String,
  val description: String?,
  val price: BigDecimal,
  val category: String,
  val images: List<String>,
  val rating: BigDecimal,
  val reviewCount: Int,
)

data class WebStatsSummary(
  val usersTotal: Long,
  val usersActive: Long,
  val productsActive: Long,
  val salesUnits: Long,
)

data class WebDashboardResponse(
  val profile: WebProfileResponse,
  val popularProducts: List<WebProductResponse>,
  val stats: WebStatsSummary,
  val links: Map<String, String>,
)

data class WebProductPageResponse(
  val items: List<WebProductResponse>,
  val page: Int,
  val size: Int,
  val totalElements: Long,
  val links: Map<String, String>,
)
