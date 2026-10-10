package com.andver.ratelimit.redis

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.metrics.RateLimitMetrics
import com.andver.ratelimit.model.RateLimitDecision
import com.andver.ratelimit.model.ResolvedRule
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scripting.support.ResourceScriptSource
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.Locale
import java.util.UUID

class RedisRateLimiter(
  private val shards: RedisShardRegistry,
  private val keyPrefix: String,
  private val metrics: RateLimitMetrics,
) : RateLimiter {

  private val fixedWindow = script("lua/fixed_window.lua")
  private val slidingLog = script("lua/sliding_window_log.lua")
  private val slidingCounter = script("lua/sliding_window_counter.lua")
  private val tokenBucket = script("lua/token_bucket.lua")

  override fun check(rule: ResolvedRule, identity: String): Mono<RateLimitDecision> {
    val shard = shards.resolve(identity)
    val key = RedisKeys.base(keyPrefix, rule.algorithm, rule.name, identity)
    val windowMs = rule.window.toMillis().coerceAtLeast(1)
    val started = System.nanoTime()
    val call = execute(shard.redis, rule, key, windowMs)
    return call.next()
      .map { raw -> parse(raw, rule, shard.name) }
      .doOnNext { decision -> metrics.record(decision, Duration.ofNanos(System.nanoTime() - started)) }
      .onErrorMap { error -> ShardAwareException(shard.name, error) }
  }

  private fun execute(
    redis: ReactiveStringRedisTemplate,
    rule: ResolvedRule,
    key: String,
    windowMs: Long,
  ) = when (rule.algorithm) {
    RateLimitAlgorithm.FIXED_WINDOW -> redis.execute(
      fixedWindow,
      listOf(key),
      listOf(rule.limit.toString(), windowMs.toString()),
    )
    RateLimitAlgorithm.SLIDING_WINDOW_LOG -> redis.execute(
      slidingLog,
      listOf(key),
      listOf(rule.limit.toString(), windowMs.toString(), UUID.randomUUID().toString()),
    )
    RateLimitAlgorithm.SLIDING_WINDOW_COUNTER -> redis.execute(
      slidingCounter,
      listOf(key),
      listOf(rule.limit.toString(), windowMs.toString()),
    )
    RateLimitAlgorithm.TOKEN_BUCKET -> redis.execute(
      tokenBucket,
      listOf(key),
      listOf(
        rule.burst.toString(),
        String.format(Locale.US, "%.10f", rule.limit.toDouble() / windowMs),
        tokenTtlMs(rule, windowMs).toString(),
      ),
    )
  }

  private fun tokenTtlMs(rule: ResolvedRule, windowMs: Long): Long {
    val refillPerMs = rule.limit.toDouble() / windowMs
    val fillMs = if (refillPerMs > 0) (rule.burst / refillPerMs).toLong() else windowMs
    return fillMs + windowMs
  }

  private fun parse(raw: List<*>, rule: ResolvedRule, shard: String): RateLimitDecision {
    val allowed = number(raw[0]) == 1L
    val limit = number(raw[1])
    val remaining = number(raw[2]).coerceAtLeast(0)
    val retryMs = number(raw[3]).coerceAtLeast(0)
    val resetMs = number(raw[4]).coerceAtLeast(0)
    return RateLimitDecision(
      allowed = allowed,
      rule = rule.name,
      algorithm = rule.algorithm,
      limit = limit,
      remaining = remaining,
      retryAfter = Duration.ofMillis(retryMs),
      resetAfter = Duration.ofMillis(resetMs),
      window = rule.window,
      burst = rule.burst,
      shard = shard,
    )
  }

  private fun number(value: Any?): Long = value?.toString()?.toDouble()?.toLong()
    ?: throw IllegalStateException("redis rate-limit script returned no number")

  private fun script(path: String): DefaultRedisScript<List<*>> =
    DefaultRedisScript<List<*>>().apply {
      setScriptSource(ResourceScriptSource(ClassPathResource(path)))
      setResultType(List::class.java)
    }
}

/** Carries the shard name so the web filter can tag redis_error metrics correctly. */
class ShardAwareException(
  val shard: String,
  cause: Throwable,
) : RuntimeException(cause.message, cause)
