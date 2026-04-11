package com.andver.bff.admin.service

import com.andver.bff.admin.backend.CreateProductBackendRequest
import com.andver.bff.admin.backend.ProductBackendDto
import com.andver.bff.admin.backend.UpdateProductBackendRequest
import com.andver.bff.admin.backend.UpdateUserBackendRequest
import com.andver.bff.admin.backend.UserBackendDto
import com.andver.bff.admin.client.ProductBackendClient
import com.andver.bff.admin.client.UserBackendClient
import com.andver.bff.admin.model.AdminCreateProductRequest
import com.andver.bff.admin.model.AdminDashboardResponse
import com.andver.bff.admin.model.AdminProductResponse
import com.andver.bff.admin.model.AdminUpdateProductRequest
import com.andver.bff.admin.model.AdminUpdateUserRequest
import com.andver.bff.admin.model.AdminUserPageResponse
import com.andver.bff.admin.model.AdminUserResponse
import com.andver.bff.admin.model.ProductMetrics
import com.andver.bff.admin.model.UserMetrics
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

@Service
class AdminBffService(
  private val userBackendClient: UserBackendClient,
  private val productBackendClient: ProductBackendClient,
) {

  @CircuitBreaker(name = "adminDashboard", fallbackMethod = "dashboardFallback")
  fun dashboard(correlationId: String): Mono<AdminDashboardResponse> {
    return Mono.zip(userBackendClient.stats(correlationId), productBackendClient.stats(correlationId))
      .map { (userStats, productStats) ->
        AdminDashboardResponse(
          users = UserMetrics(total = userStats.totalUsers, active = userStats.activeUsers),
          products = ProductMetrics(
            total = productStats.totalProducts,
            active = productStats.activeProducts,
            salesUnits = productStats.totalSalesUnits,
          ),
        )
      }
  }

  fun dashboardFallback(correlationId: String, ex: Throwable): Mono<AdminDashboardResponse> =
    Mono.just(
      AdminDashboardResponse(
        users = UserMetrics(0, 0),
        products = ProductMetrics(0, 0, 0),
      ),
    )

  fun usersPage(correlationId: String, page: Int, size: Int): Mono<AdminUserPageResponse> {
    return userBackendClient.list(correlationId)
      .collectList()
      .map { all ->
        val total = all.size.toLong()
        val from = page.coerceAtLeast(0) * size.coerceAtLeast(1)
        val slice = all.drop(from).take(size.coerceAtLeast(1)).map { it.toAdminUser() }
        AdminUserPageResponse(
          content = slice,
          page = page,
          size = size,
          totalElements = total,
        )
      }
  }

  fun updateUser(id: Long, req: AdminUpdateUserRequest, correlationId: String): Mono<AdminUserResponse> {
    val body = UpdateUserBackendRequest(
      email = req.email,
      name = req.name,
      isActive = req.isActive,
    )
    return userBackendClient.update(id, body, correlationId).map { it.toAdminUser() }
  }

  fun productById(id: Long, correlationId: String): Mono<AdminProductResponse> =
    productBackendClient.getProduct(id, correlationId).map { it.toAdminProduct() }

  fun createProduct(req: AdminCreateProductRequest, correlationId: String): Mono<AdminProductResponse> {
    val body = CreateProductBackendRequest(
      name = req.name,
      description = req.description,
      price = req.price,
      category = req.category,
      images = req.images,
      createdBy = "admin-bff",
    )
    return productBackendClient.create(body, correlationId).map { it.toAdminProduct() }
  }

  fun updateProduct(id: Long, req: AdminUpdateProductRequest, correlationId: String): Mono<AdminProductResponse> {
    val body = UpdateProductBackendRequest(
      name = req.name,
      description = req.description,
      price = req.price,
      category = req.category,
      images = req.images,
      isActive = req.isActive,
    )
    return productBackendClient.update(id, body, correlationId).map { it.toAdminProduct() }
  }

  fun bulkProducts(requests: List<AdminCreateProductRequest>, correlationId: String): Flux<AdminProductResponse> {
    val items = requests.map {
      CreateProductBackendRequest(
        name = it.name,
        description = it.description,
        price = it.price,
        category = it.category,
        images = it.images,
        createdBy = "admin-bff",
      )
    }
    return productBackendClient.bulk(items, correlationId).map { it.toAdminProduct() }
  }

  private fun UserBackendDto.toAdminUser() = AdminUserResponse(
    id = id,
    email = email,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isActive = isActive,
  )

  private fun ProductBackendDto.toAdminProduct() = AdminProductResponse(
    id = id,
    name = name,
    description = description,
    price = price,
    category = category,
    images = images?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    rating = rating,
    reviewCount = reviewCount,
    salesCount = salesCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isActive = isActive,
  )
}
