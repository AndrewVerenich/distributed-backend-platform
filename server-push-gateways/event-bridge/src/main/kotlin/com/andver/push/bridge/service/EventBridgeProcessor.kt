package com.andver.push.bridge.service

import com.andver.push.model.PUSH_CHANNEL_PREFIX
import com.andver.push.model.PUSH_EVENT_SEQ_KEY
import com.andver.push.model.PUSH_REPLAY_MAX_SIZE
import com.andver.push.model.PUSH_REPLAY_PREFIX
import com.andver.push.model.PushEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

interface EventBridgeProcessor {
  fun process(event: PushEvent)
}

@Service
class DefaultEventBridgeProcessor(
  private val stringRedisTemplate: StringRedisTemplate,
  private val objectMapper: ObjectMapper,
) : EventBridgeProcessor {

  private val log = LoggerFactory.getLogger(DefaultEventBridgeProcessor::class.java)

  override fun process(event: PushEvent) {
    val eventId = stringRedisTemplate.opsForValue().increment(PUSH_EVENT_SEQ_KEY) ?: 1L
    val publishedAt = (if (event.publishedAt == Instant.EPOCH) Instant.now() else event.publishedAt)
      .truncatedTo(ChronoUnit.MILLIS)
    val withId = event.copy(
      eventId = eventId,
      publishedAt = publishedAt,
    )
    val json = objectMapper.writeValueAsString(withId)

    val replayKey = "$PUSH_REPLAY_PREFIX:${withId.clientId}"
    stringRedisTemplate.opsForList().leftPush(replayKey, json)
    stringRedisTemplate.opsForList().trim(replayKey, 0, PUSH_REPLAY_MAX_SIZE - 1)

    val channel = "$PUSH_CHANNEL_PREFIX:${withId.clientId}"
    val receivers = stringRedisTemplate.convertAndSend(channel, json)
    log.info(
      "Bridged eventId={} clientId={} type={} receivers={}",
      withId.eventId,
      withId.clientId,
      withId.type,
      receivers,
    )
  }
}
