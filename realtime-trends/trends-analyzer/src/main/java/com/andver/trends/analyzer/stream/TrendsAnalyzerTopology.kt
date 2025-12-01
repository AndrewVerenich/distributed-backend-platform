package com.andver.trends.analyzer.stream

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.TimeWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class TrendsAnalyzerTopology(
  @Value("\${product-view.topic}") private val topicName: String,
  @Value("\${product-view.count-topic}") private val countTopicName: String,
  @Value("\${product-view.window}") private val window: String,
  @Value("\${product-view.threshold}") private val threshold: Long,
) {
  private val objectMapper = jacksonObjectMapper()
  private final val logger: Logger = LoggerFactory.getLogger(TrendsAnalyzerTopology::class.java)

  @Bean
  fun countStream(builder: StreamsBuilder): KStream<Long, String> {
    return builder.stream<Long, String>(topicName).apply {
      val productCategoryMap = mutableMapOf<Long, Long>()

      val productCounts = groupBy { _, value ->
        val event: ProductViewEvent = objectMapper.readValue(value)
        productCategoryMap[event.productId] = event.categoryId
        event.productId
      }
        .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.parse(window)))
        .count(Materialized.`as`("product-view-counts"))

      productCounts.toStream()
        .filter { _, count -> count > threshold }
        .map { windowedKey, count ->
          val productId = windowedKey.key()
          val categoryId = productCategoryMap[productId] ?: -1L
          val payload = ProductViewCount(views = count, categoryId = categoryId, ts = System.currentTimeMillis())
          KeyValue(productId, objectMapper.writeValueAsString(payload))
        }
        .peek { productId, count ->
          logger.info("Product $productId views = $count")
        }
        .to(
          countTopicName,
          Produced.with(Serdes.Long(), Serdes.String())
        )
    }
  }
}

data class ProductViewEvent(
  val userId: Long,
  val productId: Long,
  val categoryId: Long,
  val timestamp: Long,
)

data class ProductViewCount(
  val views: Long,
  val categoryId: Long,
  val ts: Long,
)
