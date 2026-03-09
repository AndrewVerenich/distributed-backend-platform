package com.andver.counter.consumer

import com.andver.counter.model.VideoViewEvent
import com.andver.counter.service.CounterService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class VideoViewConsumer(
  private val counterService: CounterService,
  private val objectMapper: ObjectMapper,
) {

  private val logger = LoggerFactory.getLogger(VideoViewConsumer::class.java)

  @KafkaListener(
    topics = ["\${counter.video-view-topic}"],
    groupId = "counter-service-unique",
  )
  fun consume(record: ConsumerRecord<Long, String>) {
    val event: VideoViewEvent = objectMapper.readValue(record.value())
    logger.info("Tracking unique viewer: userId={} videoId={}", event.userId, event.videoId)
    counterService.trackUniqueViewer(event.videoId, event.userId)
      .subscribe()
  }
}
