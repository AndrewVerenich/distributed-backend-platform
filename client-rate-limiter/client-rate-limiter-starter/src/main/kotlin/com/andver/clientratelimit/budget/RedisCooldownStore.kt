package com.andver.clientratelimit.budget

import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration

class RedisCooldownStore(
  private val redis: ReactiveStringRedisTemplate,
  private val keyPrefix: String,
) : CooldownStore {

  override fun remaining(ruleName: String): Mono<Duration> {
    val key = key(ruleName)
    return redis.getExpire(key)
      .defaultIfEmpty(Duration.ZERO)
      .map { ttl ->
        if (ttl.isNegative || ttl.isZero) Duration.ZERO else ttl
      }
  }

  override fun set(ruleName: String, wait: Duration): Mono<Void> {
    if (wait.isZero || wait.isNegative) {
      return Mono.empty()
    }
    val key = key(ruleName)
    return redis.opsForValue().set(key, "1", wait).then()
  }

  private fun key(ruleName: String): String = "$keyPrefix:cooldown:$ruleName"
}
