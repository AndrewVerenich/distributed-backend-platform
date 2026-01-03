package com.andver.gateway.client.notification.processor

import com.andver.gateway.client.notification.model.InternalDomainEvent
import org.apache.logging.log4j.LogManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

interface DomainServerEventProcessor {
  fun process(event: InternalDomainEvent)
}

@Component
class DefaultDomainServerEventProcessor(
  @Value("\${user.subscription.prefix}")
  private val userSubscriptionPrefix: String,
  private val serverWebsocketRedisTemplate: RedisTemplate<String, InternalDomainEvent>
) : DomainServerEventProcessor {

  private val logger = LogManager.getLogger(DefaultDomainServerEventProcessor::class.java)
  override fun process(event: InternalDomainEvent) {
    val userId = event.userId

    val numberOfReceivers = serverWebsocketRedisTemplate.convertAndSend("$userSubscriptionPrefix:$userId", event)
    if (numberOfReceivers == 0L) {
      logger.info("<--- No channels for event $event")
    } else {
      logger.info("<--- Sent websocket event to client event=$event, number of channels=$numberOfReceivers")
    }
  }
}
