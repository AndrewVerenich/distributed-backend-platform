package com.andver.gateway.client.notification.config

import com.andver.gateway.client.notification.model.InternalDomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.stereotype.Component

@Component
class RedisConfig(
  private val objectMapper: ObjectMapper,
) {
  @Bean
  fun serverWebsocketRedisTemplate(
    connectionFactory: RedisConnectionFactory
  ): RedisTemplate<String, InternalDomainEvent> {
    val template = RedisTemplate<String, InternalDomainEvent>()
    val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
    template.connectionFactory = connectionFactory
    template.keySerializer = StringRedisSerializer()
    template.valueSerializer = jsonSerializer
    template.hashKeySerializer = StringRedisSerializer()
    template.hashValueSerializer = jsonSerializer
    template.afterPropertiesSet()
    return template
  }
}