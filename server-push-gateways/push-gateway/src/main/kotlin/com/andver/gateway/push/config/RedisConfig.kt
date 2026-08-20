package com.andver.gateway.push.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisConfig {

  @Bean
  fun objectMapper(): ObjectMapper {
    return ObjectMapper()
      .registerModule(kotlinModule())
      .registerModule(JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Bean
  fun reactiveStringRedisTemplate(
    connectionFactory: ReactiveRedisConnectionFactory,
  ): ReactiveStringRedisTemplate {
    return ReactiveStringRedisTemplate(connectionFactory)
  }

  @Bean
  fun reactiveRedisTemplate(
    connectionFactory: ReactiveRedisConnectionFactory,
  ): ReactiveRedisTemplate<String, String> {
    val serializer = StringRedisSerializer()
    val context = RedisSerializationContext
      .newSerializationContext<String, String>(serializer)
      .value(serializer)
      .hashKey(serializer)
      .hashValue(serializer)
      .build()
    return ReactiveRedisTemplate(connectionFactory, context)
  }
}
