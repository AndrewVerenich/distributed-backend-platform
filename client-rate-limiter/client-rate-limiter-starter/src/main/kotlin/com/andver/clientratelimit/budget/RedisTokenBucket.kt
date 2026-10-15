package com.andver.clientratelimit.budget

import com.andver.clientratelimit.model.ClientRateLimitRule
import com.andver.clientratelimit.model.TokenBucketDecision
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scripting.support.ResourceScriptSource
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Locale

class RedisTokenBucket(
  private val redis: ReactiveStringRedisTemplate,
  private val keyPrefix: String,
) : TokenBucket {

  private val script = DefaultRedisScript<List<*>>().apply {
    setScriptSource(ResourceScriptSource(ClassPathResource("lua/token_bucket.lua")))
    setResultType(List::class.java)
  }

  override fun tryAcquire(rule: ClientRateLimitRule): Mono<TokenBucketDecision> {
    val windowMs = rule.window.toMillis().coerceAtLeast(1)
    val refillPerMs = rule.limit.toDouble() / windowMs
    val idleTtl = tokenTtlMs(rule, windowMs)
    val key = "$keyPrefix:token-bucket:${rule.name}"
    return redis.execute(
      script,
      listOf(key),
      listOf(
        rule.burst.toString(),
        String.format(Locale.US, "%.10f", refillPerMs),
        idleTtl.toString(),
      ),
    ).next().map { raw -> parse(raw) }
  }

  private fun tokenTtlMs(rule: ClientRateLimitRule, windowMs: Long): Long {
    val refillPerMs = rule.limit.toDouble() / windowMs
    val fillMs = if (refillPerMs > 0) (rule.burst / refillPerMs).toLong() else windowMs
    return fillMs + windowMs
  }

  private fun parse(raw: List<*>): TokenBucketDecision {
    val allowed = number(raw[0]) == 1L
    return TokenBucketDecision(
      allowed = allowed,
      limit = number(raw[1]),
      remaining = number(raw[2]).coerceAtLeast(0),
      retryAfter = Duration.ofMillis(number(raw[3]).coerceAtLeast(0)),
      resetAfter = Duration.ofMillis(number(raw[4]).coerceAtLeast(0)),
    )
  }

  private fun number(value: Any?): Long =
    value?.toString()?.toDouble()?.toLong()
      ?: throw IllegalStateException("token-bucket script returned no number")
}
