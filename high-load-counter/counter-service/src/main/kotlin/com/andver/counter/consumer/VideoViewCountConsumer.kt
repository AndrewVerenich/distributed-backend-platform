package com.andver.counter.consumer

import com.andver.counter.model.VideoViewCountMessage
import com.andver.counter.service.CounterService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class VideoViewCountConsumer(
  private val counterService: CounterService,
  private val objectMapper: ObjectMapper,
) {

  private val logger = LoggerFactory.getLogger(VideoViewCountConsumer::class.java)

  @KafkaListener(
    topics = ["\${counter.video-view-counts-topic}"],
    groupId = "counter-service-counts",
  )
  fun consume(record: ConsumerRecord<Long, String>) {
    val message: VideoViewCountMessage = objectMapper.readValue(record.value())
    logger.info("Received windowed count: videoId={} count={}", message.videoId, message.count)
    counterService.incrementViewCount(message.videoId, message.count)
      .subscribe()
  }
}
