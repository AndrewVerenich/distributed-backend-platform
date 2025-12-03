package com.andver.order.client

import com.andver.order.model.TrendyProduct
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Duration

interface KsqlDbClient {
  fun getTrendyProducts(categoryIds: List<Long>, of: Duration): Flux<TrendyProduct>
}

private const val CONTENT_TYPE = "application/vnd.ksql.v1+json; charset=utf-8"

@Component
class DefaultKsqlDbClient(
  builder: WebClient.Builder,
  @Value("\${client.ksqldb.url}") url: String,
  private val objectMapper: ObjectMapper
) : KsqlDbClient {

  private val client = builder.baseUrl(url).build()

  override fun getTrendyProducts(categoryIds: List<Long>, of: Duration): Flux<TrendyProduct> {
    val categories = categoryIds.joinToString(",")
    val sql = """
            SELECT productId, categoryId
            FROM QUERYABLE_PRODUCT_VIEW_COUNTS
            WHERE categoryId IN ($categories)
            AND ts > UNIX_TIMESTAMP() - ${of.toMillis()} 
            LIMIT 10;
        """.trimIndent()

    return client.post()
      .uri("/query")
      .header("Content-Type", CONTENT_TYPE)
      .bodyValue(mapOf("ksql" to sql))
      .retrieve()
      .bodyToFlux(String::class.java)
      .flatMap { line ->
        if (line.startsWith("{")) Mono.empty()
        else {
          buildProduct(objectMapper.readValue(line, Array<Long>::class.java))
        }
      }
  }

  private fun buildProduct(arr: Array<Long>): Mono<TrendyProduct> =
    TrendyProduct(arr[0], arr[1]).toMono()
}