package com.andver.gateway.push.service

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.push.model.PushEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

interface EventReplayService {
  fun findSince(clientId: String, since: Long): Mono<List<PushEvent>>
  fun findAfterLastEventId(clientId: String, lastEventId: Long): Mono<List<PushEvent>>
}

@Service
class DefaultEventReplayService(
  private val properties: PushGatewayProperties,
  private val redisTemplate: ReactiveStringRedisTemplate,
  private val objectMapper: ObjectMapper,
) : EventReplayService {

  override fun findSince(clientId: String, since: Long): Mono<List<PushEvent>> {
    val key = "${properties.replayPrefix}:$clientId"
    return redisTemplate.opsForList()
      .range(key, 0, properties.replayMaxSize - 1)
      .map { json -> objectMapper.readValue<PushEvent>(json) }
      .collectList()
      .map { events ->
        events
          .filter { it.eventId > since }
          .sortedBy { it.eventId }
      }
  }

  override fun findAfterLastEventId(clientId: String, lastEventId: Long): Mono<List<PushEvent>> =
    findSince(clientId, lastEventId)
}
