package com.andver.gateway.websocket.service

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

interface UserSubscriptionService {
  fun subscribeToUserNotifications(userId: Long): Flux<String>
}

@Component
class DefaultUserSubscriptionService(
  @Value("\${user.subscription.prefix}")
  private val usersSubscriptionPrefix: String,
  private val redisTemplate: ReactiveRedisTemplate<String, String>,
) : UserSubscriptionService {
  override fun subscribeToUserNotifications(userId: Long): Flux<String> {
    log.info("Subscribed userId=$userId to notifications")
    return redisTemplate.listenToChannel("$usersSubscriptionPrefix:$userId")
      .map { it.message }
  }

  companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
