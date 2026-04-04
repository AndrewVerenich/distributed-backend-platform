package com.andver.sharding.scatter

import com.andver.sharding.registry.ShardRegistry
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Flux

class ScatterGatherTemplate(
  private val shardRegistry: ShardRegistry,
) {
  fun <T> scatterGather(
    shards: Iterable<String> = shardRegistry.allShardNames(),
    query: (shardName: String, connectionFactory: ConnectionFactory) -> Flux<T>,
  ): Flux<T> {
    return Flux.fromIterable(shards)
      .flatMap({ shardName ->
        val factory = shardRegistry.connectionFactory(shardName)
        query(shardName, factory)
      })
  }
}