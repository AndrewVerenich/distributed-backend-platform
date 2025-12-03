package com.andver.order.service

import com.andver.order.client.KsqlDbClient
import com.andver.order.model.TrendyProduct
import com.andver.order.repository.UserPreferenceRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

interface ProductService {
  fun getTrendyProducts(userId: Long): Flux<TrendyProduct>
}

@Service
class DefaultProductService(
  private val userPreferenceRepository: UserPreferenceRepository,
  private val ksqlDbClient: KsqlDbClient
) : ProductService {
  override fun getTrendyProducts(userId: Long): Flux<TrendyProduct> {
    return userPreferenceRepository.findCategoriesByUserId(userId)
      .collectList()
      .flatMapMany { categories ->
        if (categories.isEmpty()) Flux.empty()
        else ksqlDbClient.getTrendyProducts(categories)
      }
  }
}