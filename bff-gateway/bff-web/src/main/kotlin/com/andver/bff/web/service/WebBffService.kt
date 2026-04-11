package com.andver.bff.web.service

import com.andver.bff.web.backend.ProductBackendDto
import com.andver.bff.web.backend.UserBackendDto
import com.andver.bff.web.client.ProductBackendClient
import com.andver.bff.web.client.UserBackendClient
import com.andver.bff.web.model.WebDashboardResponse
import com.andver.bff.web.model.WebProductPageResponse
import com.andver.bff.web.model.WebProductResponse
import com.andver.bff.web.model.WebProfileResponse
import com.andver.bff.web.model.WebStatsSummary
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.kotlin.core.util.function.component3
import reactor.kotlin.core.util.function.component4
import java.math.BigDecimal

@Service
class WebBffService(
  private val userBackendClient: UserBackendClient,
  private val productBackendClient: ProductBackendClient,
) {

  @CircuitBreaker(name = "webDashboard", fallbackMethod = "dashboardFallback")
  fun dashboard(correlationId: String, userId: Long): Mono<WebDashboardResponse> {
    val userMono = userBackendClient.getUser(userId, correlationId)
    val popularMono = productBackendClient.popular(correlationId).collectList()
    val userStatsMono = userBackendClient.stats(correlationId)
    val productStatsMono = productBackendClient.stats(correlationId)
    return Mono.zip(userMono, popularMono, userStatsMono, productStatsMono)
      .map { (user, popular, userStats, productStats) ->
        val profile = user.toWebProfile()
        val popular = popular.map { it.toWebProduct() }
        val stats = WebStatsSummary(
          usersTotal = userStats.totalUsers,
          usersActive = userStats.activeUsers,
          productsActive = productStats.activeProducts,
          salesUnits = productStats.totalSalesUnits,
        )
        WebDashboardResponse(
          profile = profile,
          popularProducts = popular,
          stats = stats,
          links = mapOf(
            "self" to "/web/dashboard",
            "products" to "/web/products",
            "profile" to "/web/users/me",
          ),
        )
      }
  }

  fun dashboardFallback(correlationId: String, userId: Long, t: Throwable): Mono<WebDashboardResponse> =
    Mono.just(
      WebDashboardResponse(
        profile = WebProfileResponse(0, "", "unavailable", false),
        popularProducts = emptyList(),
        stats = WebStatsSummary(0, 0, 0, 0),
        links = mapOf("error" to (t.message ?: "fallback")),
      ),
    )

  @CircuitBreaker(name = "webProducts", fallbackMethod = "productPageFallback")
  fun productPage(
    correlationId: String,
    page: Int,
    size: Int,
    sort: String,
    desc: Boolean,
  ): Mono<WebProductPageResponse> {
    return productBackendClient.page(page, size, sort, desc, correlationId)
      .map { p ->
        val items = p.content.map { it.toWebProduct() }
        val links = buildMap {
          put("self", "/web/products?page=$page&size=$size&sort=$sort&desc=$desc")
          if (page > 0) put("prev", "/web/products?page=${page - 1}&size=$size&sort=$sort&desc=$desc")
          if ((page + 1L) * size < p.totalElements) {
            put("next", "/web/products?page=${page + 1}&size=$size&sort=$sort&desc=$desc")
          }
        }
        WebProductPageResponse(
          items = items,
          page = p.page,
          size = p.size,
          totalElements = p.totalElements,
          links = links,
        )
      }
  }

  fun productPageFallback(
    correlationId: String,
    page: Int,
    size: Int,
    sort: String,
    desc: Boolean,
    t: Throwable,
  ): Mono<WebProductPageResponse> =
    Mono.just(WebProductPageResponse(emptyList(), page, size, 0, mapOf("error" to (t.message ?: "fallback"))))

  @CircuitBreaker(name = "webProductById", fallbackMethod = "productByIdFallback")
  fun productById(correlationId: String, id: Long): Mono<WebProductResponse> =
    productBackendClient.getProduct(id, correlationId).map { it.toWebProduct() }

  fun productByIdFallback(correlationId: String, id: Long, t: Throwable): Mono<WebProductResponse> =
    Mono.just(
      WebProductResponse(
        id = id,
        name = "unavailable",
        description = null,
        price = BigDecimal.ZERO,
        category = "",
        images = emptyList(),
        rating = BigDecimal.ZERO,
        reviewCount = 0,
      ),
    )

  @CircuitBreaker(name = "webUserMe", fallbackMethod = "userMeFallback")
  fun userMe(correlationId: String, userId: Long): Mono<WebProfileResponse> =
    userBackendClient.getUser(userId, correlationId).map { it.toWebProfile() }

  fun userMeFallback(correlationId: String, userId: Long, t: Throwable): Mono<WebProfileResponse> =
    Mono.just(WebProfileResponse(userId, "", "unavailable", false))

  private fun UserBackendDto.toWebProfile() = WebProfileResponse(
    id = id,
    email = email,
    name = name,
    isActive = isActive,
  )

  private fun ProductBackendDto.toWebProduct() = WebProductResponse(
    id = id,
    name = name,
    description = description,
    price = price,
    category = category,
    images = images?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    rating = rating,
    reviewCount = reviewCount,
  )
}
