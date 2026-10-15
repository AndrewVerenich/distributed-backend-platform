package com.andver.sharding.user.service

import com.andver.sharding.context.ShardContext
import com.andver.sharding.registry.ShardRegistry
import com.andver.sharding.resolver.ShardResolver
import com.andver.sharding.scatter.ScatterGatherTemplate
import com.andver.sharding.user.metrics.ShardingMetrics
import com.andver.sharding.user.model.UserProfile
import com.andver.sharding.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class UserProfileService(
  private val userProfileRepository: UserProfileRepository,
  private val shardResolver: ShardResolver,
  private val scatterGatherTemplate: ScatterGatherTemplate,
  private val shardRegistry: ShardRegistry,
  private val metrics: ShardingMetrics,
) {
  fun create(userId: String, name: String, email: String): Mono<UserProfile> {
    val shard = shardResolver.resolveShardForKey(userId)
    return userProfileRepository.save(UserProfile.new(userId = userId, name = name, email = email))
      .doOnSuccess { metrics.recordOp(shard, "create") }
      .routedForUser(userId)
  }

  fun get(userId: String): Mono<UserProfile> {
    val shard = shardResolver.resolveShardForKey(userId)
    return userProfileRepository.findByUserId(userId)
      .doOnSuccess { metrics.recordOp(shard, "get") }
      .routedForUser(userId)
  }

  fun update(userId: String, name: String, email: String): Mono<UserProfile> {
    val shard = shardResolver.resolveShardForKey(userId)
    return userProfileRepository.findByUserId(userId)
      .flatMap { existing ->
        userProfileRepository.save(
          existing.copy(
            name = name,
            email = email,
          ),
        )
      }
      .doOnSuccess { metrics.recordOp(shard, "update") }
      .routedForUser(userId)
  }

  fun delete(userId: String): Mono<Void> {
    val shard = shardResolver.resolveShardForKey(userId)
    return userProfileRepository.deleteByUserId(userId)
      .then()
      .doOnSuccess { metrics.recordOp(shard, "delete") }
      .routedForUser(userId)
  }

  fun searchByName(nameQuery: String, limit: Int = 50): Flux<UserProfile> {
    val like = "%$nameQuery%"
    val started = System.nanoTime()
    return scatterGatherTemplate.scatterGather { shardName, _ ->
      userProfileRepository.searchByNameIlike(like, limit)
        .contextWrite(ShardContext.withShard(shardName))
    }.doOnComplete {
      metrics.recordScatterGather(Duration.ofNanos(System.nanoTime() - started))
    }
  }

  fun countsByShard(): Mono<List<ShardCount>> {
    return Flux.fromIterable(shardRegistry.allShardNames())
      .flatMap { shardName ->
        userProfileRepository.count()
          .contextWrite(ShardContext.withShard(shardName))
          .map { count ->
            metrics.setRows(shardName, count)
            ShardCount(shard = shardName, count = count)
          }
      }
      .collectList()
  }

  private fun <T> Mono<T>.routedForUser(userId: String) =
    contextWrite(ShardContext.withShard(shardResolver.resolveShardForKey(userId)))
}

data class ShardCount(
  val shard: String,
  val count: Long,
)
