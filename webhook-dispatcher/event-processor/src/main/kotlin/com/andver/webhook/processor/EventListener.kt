package com.andver.webhook.processor

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class EventListener(
  private val fanout: FanoutService,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @KafkaListener(topics = ["\${webhook.topics.event:webhook.event}"], groupId = "webhook-event-processor")
  fun onEvent(payload: String) {
    try {
      fanout.process(payload).block()
    } catch (e: Exception) {
      log.error("fan-out failed: {}", e.message, e)
      throw e
    }
  }
}
