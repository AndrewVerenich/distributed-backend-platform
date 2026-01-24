package com.andver.time.starter.cache

import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

private const val OFFSET_KEY_PREFIX = "time:offset:"
private val OFFSET_TTL_SECONDS = Duration.ofSeconds(300)

interface OffsetCache {
  fun saveOffset(nodeId: String, offset: Long): Mono<Boolean>
  fun getOffset(nodeId: String): Mono<Long?>
}

@Component
class DefaultOffsetCache(
  private val redisTemplate: ReactiveRedisTemplate<String, String>
) : OffsetCache {

  override fun saveOffset(nodeId: String, offset: Long): Mono<Boolean> {
    val key = "$OFFSET_KEY_PREFIX$nodeId"
    return redisTemplate.opsForValue()
      .set(key, offset.toString(), OFFSET_TTL_SECONDS)
      .onErrorReturn(false)
  }

  override fun getOffset(nodeId: String): Mono<Long?> {
    val key = "$OFFSET_KEY_PREFIX$nodeId"
    return redisTemplate.opsForValue()
      .get(key)
      .mapNotNull { value -> value.toLongOrNull() }
  }
}

