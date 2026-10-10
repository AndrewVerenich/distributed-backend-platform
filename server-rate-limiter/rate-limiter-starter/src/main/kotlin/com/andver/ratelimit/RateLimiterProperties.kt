package com.andver.ratelimit

import com.andver.ratelimit.algorithm.RateLimitAlgorithm
import com.andver.ratelimit.model.FailureMode
import com.andver.ratelimit.model.KeyStrategy
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "rate-limiter")
data class RateLimiterProperties(
  /** Turn the starter off without removing it from the classpath. */
  val enabled: Boolean = true,
  /** Default algorithm. A rule may override it. */
  val algorithm: RateLimitAlgorithm = RateLimitAlgorithm.TOKEN_BUCKET,
  /** Redis key namespace. Instances that must share a quota use the same prefix. */
  val keyPrefix: String = "rl",
  /** Redis down: reject the call, or let it through. */
  val onRedisError: FailureMode = FailureMode.REJECT,
  val key: KeyProperties = KeyProperties(),
  val filter: FilterProperties = FilterProperties(),
  val sharding: ShardingProperties = ShardingProperties(),
  val rules: List<RuleProperties> = emptyList(),
) {
  data class KeyProperties(
    val strategy: KeyStrategy = KeyStrategy.HEADER,
    /** Read when [strategy] is [KeyStrategy.HEADER]. */
    val header: String = "X-User-Id",
    /** Header missing or blank → remote address, so anonymous traffic still shares a bucket. */
    val fallbackToIp: Boolean = true,
  )

  data class FilterProperties(
    val enabled: Boolean = true,
    val excludePathPrefixes: List<String> = listOf("/actuator"),
  )

  /**
   * Application-level Redis sharding by identity hash.
   * When disabled, the starter uses Spring's single [org.springframework.data.redis.core.ReactiveStringRedisTemplate].
   */
  data class ShardingProperties(
    val enabled: Boolean = false,
    /** Shard label used when [enabled] is false. */
    val singleShardName: String = "default",
    val nodes: List<ShardNodeProperties> = emptyList(),
  )

  data class ShardNodeProperties(
    val name: String = "",
    val host: String = "localhost",
    val port: Int = 6379,
  )

  data class RuleProperties(
    val name: String = "default",
    val pathPrefixes: List<String> = listOf("/"),
    /** Null inherits [RateLimiterProperties.algorithm]. */
    val algorithm: RateLimitAlgorithm? = null,
    /** Sustained allowance per [window]. */
    val limit: Long = 10,
    val window: Duration = Duration.ofSeconds(1),
    /**
     * Token bucket capacity. `0` means "same as limit".
     * Fixed and sliding windows ignore this: their cap is [limit].
     */
    val burst: Long = 0,
    val keyStrategy: KeyStrategy? = null,
    val keyHeader: String? = null,
  )
}
