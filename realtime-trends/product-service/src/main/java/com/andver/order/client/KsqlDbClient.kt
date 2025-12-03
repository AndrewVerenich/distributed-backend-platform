package com.andver.order.client

import com.andver.order.model.TrendyProduct
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

interface KsqlDbClient {
  fun getTrendyProducts(categoryIds: List<Long>): Flux<TrendyProduct>
}

@Component
class DefaultKsqlDbClient(
  builder: WebClient.Builder,
  @Value("\${client.ksqldb.url}") url: String,
  private val objectMapper: ObjectMapper
) : KsqlDbClient {

  private val client = builder.baseUrl(url).build()

  override fun getTrendyProducts(categoryIds: List<Long>): Flux<TrendyProduct> {
    val categories = categoryIds.joinToString(",")
    val sql = """
            SELECT productId, categoryId, views, ts
            FROM QUERYABLE_PRODUCT_VIEW_COUNTS
            WHERE categoryId IN ($categories)
            AND ts > UNIX_TIMESTAMP() - 3600000 
            LIMIT 10;
        """.trimIndent()

    return client.post()
      .uri("/query")
      .header("Content-Type", "application/vnd.ksql.v1+json; charset=utf-8")
      .bodyValue(mapOf("ksql" to sql))
      .retrieve()
      .bodyToFlux(String::class.java)
      .flatMap { line ->
        if (line.startsWith("{")) Mono.empty()
        else {
          val arr: Array<Long> = objectMapper.readValue(line, Array<Long>::class.java)
          TrendyProduct(arr[0], arr[1], arr[2], arr[3]).toMono()
        }
      }
  }
}