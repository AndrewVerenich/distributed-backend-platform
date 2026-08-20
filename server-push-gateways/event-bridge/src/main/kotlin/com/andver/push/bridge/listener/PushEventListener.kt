package com.andver.push.bridge.listener

import com.andver.push.bridge.service.EventBridgeProcessor
import com.andver.push.model.PUSH_SERVER_EVENT_TOPIC
import com.andver.push.model.PushEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

interface PushEventListener {
  fun consume(payload: PushEvent)
}

@Component
class DefaultPushEventListener(
  private val processor: EventBridgeProcessor,
) : PushEventListener {

  @KafkaListener(
    topics = [PUSH_SERVER_EVENT_TOPIC],
    groupId = "push.event.bridge",
  )
  override fun consume(@Payload payload: PushEvent) {
    processor.process(payload)
  }
}
