package com.andver.ratelimit.redis

import com.andver.ratelimit.RateLimiterProperties
import org.springframework.beans.factory.DisposableBean
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

data class RedisShard(
  val name: String,
  val index: Int,
  val redis: ReactiveStringRedisTemplate,
)

class RedisShardRegistry(
  private val shards: List<RedisShard>,
  private val ownedFactories: List<LettuceConnectionFactory> = emptyList(),
) : DisposableBean {

  private val selector = IdentityHashShardSelector(shards.size)

  init {
    require(shards.isNotEmpty()) { "at least one Redis shard is required" }
  }

  fun resolve(identity: String): RedisShard = shards[selector.index(identity)]

  fun all(): List<RedisShard> = shards

  fun size(): Int = shards.size

  override fun destroy() {
    ownedFactories.forEach { factory ->
      runCatching { factory.destroy() }
    }
  }

  companion object {
    fun single(name: String, redis: ReactiveStringRedisTemplate): RedisShardRegistry =
      RedisShardRegistry(listOf(RedisShard(name = name, index = 0, redis = redis)))

    fun fromNodes(nodes: List<RateLimiterProperties.ShardNodeProperties>): RedisShardRegistry {
      require(nodes.isNotEmpty()) {
        "rate-limiter.sharding.enabled=true requires rate-limiter.sharding.nodes"
      }
      val factories = ArrayList<LettuceConnectionFactory>(nodes.size)
      val shards = nodes.mapIndexed { index, node ->
        require(node.host.isNotBlank()) { "sharding node[$index] host must not be blank" }
        require(node.port in 1..65535) { "sharding node[$index] port is invalid: ${node.port}" }
        val factory = LettuceConnectionFactory(node.host, node.port)
        factory.afterPropertiesSet()
        factories += factory
        val serializer = StringRedisSerializer()
        val context = RedisSerializationContext.newSerializationContext<String, String>(serializer).build()
        val name = node.name.ifBlank { "shard-$index" }
        RedisShard(name = name, index = index, redis = ReactiveStringRedisTemplate(factory, context))
      }
      return RedisShardRegistry(shards, factories)
    }
  }
}
