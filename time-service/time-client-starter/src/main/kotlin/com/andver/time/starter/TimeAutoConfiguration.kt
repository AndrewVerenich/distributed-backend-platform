package com.andver.time.starter

import com.andver.time.starter.cache.DefaultOffsetCache
import com.andver.time.starter.cache.OffsetCache
import com.andver.time.starter.client.DefaultTimeServiceClient
import com.andver.time.starter.client.TimeServiceClient
import com.andver.time.starter.properties.TimeProperties
import com.andver.time.starter.scheduler.TimeSyncScheduler
import com.andver.time.starter.service.DefaultLogicalTimeService
import com.andver.time.starter.service.DefaultOffsetProvider
import com.andver.time.starter.service.LogicalTimeService
import com.andver.time.starter.service.OffsetProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(TimeProperties::class)
class TimeAutoConfiguration {

  @Bean
  fun timeServiceWebClient(properties: TimeProperties): WebClient {
    return WebClient.builder()
      .baseUrl(properties.serviceUrl)
      .build()
  }

  @Bean
  fun timeServiceClient(webClient: WebClient): TimeServiceClient {
    return DefaultTimeServiceClient(webClient)
  }

  @Bean
  fun logicalTimeService(offsetProvider: OffsetProvider): LogicalTimeService {
    return DefaultLogicalTimeService(offsetProvider)
  }

  @Bean
  fun offsetProvider(
    offsetCache: OffsetCache,
    @Value("\${time.node-id:unknown}") nodeId: String
  ): OffsetProvider {
    return DefaultOffsetProvider(offsetCache, nodeId)
  }

  @Bean
  @ConditionalOnProperty(name = ["time.sync.enabled"], havingValue = "true", matchIfMissing = true)
  fun timeSyncScheduler(
    @Value("\${time.node-id:unknown}") nodeId: String,
    timeServiceClient: TimeServiceClient,
    offsetProvider: OffsetProvider
  ): TimeSyncScheduler {
    return TimeSyncScheduler(timeServiceClient, offsetProvider, nodeId)
  }

  @Bean
  fun objectMapper(): ObjectMapper {
    return jacksonObjectMapper()
  }

  @Bean
  fun offsetCache(redisConnectionFactory: ReactiveRedisConnectionFactory): OffsetCache {
    val serializer = StringRedisSerializer()
    val serializationContext = RedisSerializationContext
      .newSerializationContext<String, String>(serializer)
      .key(serializer)
      .value(serializer)
      .hashKey(serializer)
      .hashValue(serializer)
      .build()

    val redisTemplate = ReactiveRedisTemplate(redisConnectionFactory, serializationContext)
    return DefaultOffsetCache(redisTemplate)
  }
}



