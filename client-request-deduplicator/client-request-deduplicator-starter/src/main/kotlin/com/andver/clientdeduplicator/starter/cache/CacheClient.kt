package com.andver.clientdeduplicator.starter.cache

import org.springframework.data.redis.core.ReactiveRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration

interface CacheClient {
  fun get(key: String): Mono<String>
  fun set(key: String, value: String, ttl: Duration): Mono<Boolean>
}

class RedisCacheClient(
  private val redisTemplate: ReactiveRedisTemplate<String, String>
) : CacheClient {
  override fun get(key: String): Mono<String> {
    return redisTemplate.opsForValue().get(key)
  }

  override fun set(
    key: String,
    value: String,
    ttl: Duration,
  ): Mono<Boolean> {
    return redisTemplate.opsForValue().set(key, value, ttl)
  }
}