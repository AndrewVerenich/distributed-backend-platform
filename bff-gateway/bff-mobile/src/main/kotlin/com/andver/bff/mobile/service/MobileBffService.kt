package com.andver.bff.mobile.service

import com.andver.bff.mobile.backend.ProductBackendDto
import com.andver.bff.mobile.backend.UserBackendDto
import com.andver.bff.mobile.client.ProductBackendClient
import com.andver.bff.mobile.client.UserBackendClient
import com.andver.bff.mobile.model.MobileFeedResponse
import com.andver.bff.mobile.model.MobileProductCompact
import com.andver.bff.mobile.model.MobileProductCursorResponse
import com.andver.bff.mobile.model.MobileProfileCompact
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.kotlin.core.util.function.component3
import java.math.BigDecimal

@Service
class MobileBffService(
  private val userBackendClient: UserBackendClient,
  private val productBackendClient: ProductBackendClient,
) {

  @CircuitBreaker(name = "mobileFeed", fallbackMethod = "feedFallback")
  fun feed(correlationId: String, userId: Long): Mono<MobileFeedResponse> {
    val userMono = userBackendClient.getUser(userId, correlationId)
    val popularMono = productBackendClient.popular(correlationId).map { it.toMobileProduct() }.collectList()
    val cursorMono = productBackendClient.cursor(0, 10, correlationId)
    return Mono.zip(userMono, popularMono, cursorMono)
      .map { (user, popular, cursor) ->
        MobileFeedResponse(
          profile = user.toMobileProfile(),
          highlights = popular,
          feed = cursor.items.map { it.toMobileProduct() },
          feedNextCursor = cursor.nextCursor,
        )
      }
  }

  fun feedFallback(correlationId: String, userId: Long, ex: Throwable): Mono<MobileFeedResponse> =
    Mono.just(
      MobileFeedResponse(
        profile = MobileProfileCompact(userId, "unavailable"),
        highlights = emptyList(),
        feed = emptyList(),
        feedNextCursor = null,
      ),
    )

  @CircuitBreaker(name = "mobileProductsCursor", fallbackMethod = "productsCursorFallback")
  fun productsCursor(correlationId: String, cursor: Long, limit: Int): Mono<MobileProductCursorResponse> {
    return productBackendClient.cursor(cursor, limit, correlationId)
      .map { page ->
        MobileProductCursorResponse(
          items = page.items.map { it.toMobileProduct() },
          nextCursor = page.nextCursor,
        )
      }
  }

  fun productsCursorFallback(
    correlationId: String,
    cursor: Long,
    limit: Int,
    ex: Throwable
  ): Mono<MobileProductCursorResponse> =
    Mono.just(MobileProductCursorResponse(emptyList(), null))

  @CircuitBreaker(name = "mobileProductById", fallbackMethod = "productByIdFallback")
  fun productById(correlationId: String, id: Long): Mono<MobileProductCompact> =
    productBackendClient.getProduct(id, correlationId).map { it.toMobileProduct() }

  fun productByIdFallback(correlationId: String, id: Long, ex: Throwable): Mono<MobileProductCompact> =
    Mono.just(MobileProductCompact(id, "unavailable", BigDecimal.ZERO, ""))

  private fun UserBackendDto.toMobileProfile() = MobileProfileCompact(id = id, name = name)

  private fun ProductBackendDto.toMobileProduct(): MobileProductCompact {
    val first = images?.split(",")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
    val thumb = first?.let { "$it?w=400&q=80" } ?: ""
    return MobileProductCompact(id = id, name = name, price = price, thumbnailUrl = thumb)
  }
}
