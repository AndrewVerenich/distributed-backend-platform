package com.andver.counter.aggregator.stream

import com.andver.counter.aggregator.properties.CounterAggregationProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Suppressed
import org.apache.kafka.streams.kstream.TimeWindows
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CounterAggregatorTopology(
  private val props: CounterAggregationProperties,
) {

  private val objectMapper = jacksonObjectMapper()
  private val logger = LoggerFactory.getLogger(CounterAggregatorTopology::class.java)

  @Bean
  fun countStream(builder: StreamsBuilder): KStream<Long, String> {
    return builder.stream<Long, String>(props.inputTopic).apply {
      groupBy(
        { _, value ->
          val event: VideoViewEvent = objectMapper.readValue(value)
          event.videoId
        },
        Grouped.with(Serdes.Long(), Serdes.String())
      )
        .windowedBy(TimeWindows.ofSizeAndGrace(props.windowSize, props.gracePeriod))
        .count(Materialized.`as`("video-view-window-counts"))
        .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
        .toStream()
        .map { windowedKey, count ->
          val videoId = windowedKey.key()
          val payload = VideoViewCountMessage(
            videoId = videoId,
            count = count,
            windowStart = windowedKey.window().startTime().toEpochMilli(),
            windowEnd = windowedKey.window().endTime().toEpochMilli(),
          )
          KeyValue(videoId, objectMapper.writeValueAsString(payload))
        }
        .peek { videoId, payload ->
          logger.info("Window closed: videoId={} payload={}", videoId, payload)
        }
        .to(props.outputTopic, Produced.with(Serdes.Long(), Serdes.String()))
    }
  }
}

data class VideoViewEvent(
  val userId: Long,
  val videoId: Long,
  val timestamp: Long,
)

data class VideoViewCountMessage(
  val videoId: Long,
  val count: Long,
  val windowStart: Long,
  val windowEnd: Long,
)
