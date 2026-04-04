package com.andver.sharding.routing

import com.andver.sharding.context.ShardContext
import org.springframework.r2dbc.connection.lookup.AbstractRoutingConnectionFactory
import reactor.core.publisher.Mono

/**
 * Routes R2DBC connections to a specific shard based on Reactor Context value.
 *
 * Context key: [com.andver.sharding.context.ShardContext.SHARD_KEY]
 */
class ShardRoutingConnectionFactory(
  private val defaultShardName: String,
) : AbstractRoutingConnectionFactory() {

  override fun determineCurrentLookupKey(): Mono<Any> {
    return Mono.deferContextual { ctx ->
      val shardName = if (ctx.hasKey(ShardContext.SHARD_KEY)) {
        ctx.get(ShardContext.SHARD_KEY)
      } else {
        defaultShardName
      }
      Mono.just(shardName)
    }
  }
}