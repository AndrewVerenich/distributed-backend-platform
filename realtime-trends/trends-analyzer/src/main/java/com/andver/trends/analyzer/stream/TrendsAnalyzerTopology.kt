package com.andver.trends.analyzer.stream

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TrendsAnalyzerTopology(
  @Value("\${product-view.topic}") private val topicName: String,
  @Value("\${product-view.count-topic}") private val countTopicName: String,
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
      }.count(
        Materialized.`as`<Long, Long, KeyValueStore<Bytes, ByteArray>>("product-view-counts")
          .withKeySerde(Serdes.Long())
          .withValueSerde(Serdes.Long())
      )

      productCounts.toStream()
        .map { key, count ->
          val categoryId = productCategoryMap[key] ?: -1L
          val payload = ProductViewCount(views = count, categoryId = categoryId)
          KeyValue(key, objectMapper.writeValueAsString(payload))
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
)
