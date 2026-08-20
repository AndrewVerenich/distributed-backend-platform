package com.andver.gateway.push.service

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.push.model.PushEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

interface ClientChannelService {
  fun subscribe(clientId: String): Flux<PushEvent>
}

@Service
class DefaultClientChannelService(
  private val properties: PushGatewayProperties,
  private val redisTemplate: ReactiveStringRedisTemplate,
  private val objectMapper: ObjectMapper,
  private val metrics: DeliveryMetrics,
) : ClientChannelService {

  private val log = LoggerFactory.getLogger(DefaultClientChannelService::class.java)

  override fun subscribe(clientId: String): Flux<PushEvent> {
    val channel = "${properties.channelPrefix}:$clientId"
    log.debug("Subscribing to Redis channel={}", channel)
    return redisTemplate.listenToChannel(channel)
      .doOnNext { metrics.recordRedisMessage() }
      .map { message -> objectMapper.readValue<PushEvent>(message.message) }
  }
}
