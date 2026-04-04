package com.andver.sharding.context

import reactor.core.publisher.Mono
import reactor.util.context.Context

object ShardContext {
  const val SHARD_KEY: String = "sharding.shard"

  fun withShard(shardName: String): Context = Context.of(SHARD_KEY, shardName)

  fun currentShard(): Mono<String> = Mono.deferContextual { ctx ->
    Mono.justOrEmpty(ctx.getOrDefault(SHARD_KEY, null as String?))
  }
}