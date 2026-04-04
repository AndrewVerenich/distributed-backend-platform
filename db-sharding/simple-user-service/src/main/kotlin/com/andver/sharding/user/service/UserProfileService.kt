package com.andver.sharding.user.service

import com.andver.sharding.context.ShardContext
import com.andver.sharding.registry.ShardRegistry
import com.andver.sharding.resolver.ShardResolver
import com.andver.sharding.scatter.ScatterGatherTemplate
import com.andver.sharding.user.model.UserProfile
import com.andver.sharding.user.repository.UserProfileRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class UserProfileService(
  private val userProfileRepository: UserProfileRepository,
  private val shardResolver: ShardResolver,
  private val scatterGatherTemplate: ScatterGatherTemplate,
  private val shardRegistry: ShardRegistry,
) {
  fun create(userId: String, name: String, email: String): Mono<UserProfile> {
    return userProfileRepository.save(UserProfile.new(userId = userId, name = name, email = email))
      .routedForUser(userId)
  }

  fun get(userId: String): Mono<UserProfile> {
    return userProfileRepository.findByUserId(userId)
      .routedForUser(userId)
  }

  fun update(userId: String, name: String, email: String): Mono<UserProfile> {
    return userProfileRepository.findByUserId(userId)
      .flatMap { existing ->
        userProfileRepository.save(
          existing.copy(
            name = name,
            email = email,
          ),
        )
      }
      .routedForUser(userId)
  }

  fun delete(userId: String): Mono<Void> {
    return userProfileRepository.deleteByUserId(userId)
      .then()
      .routedForUser(userId)
  }

  fun searchByName(nameQuery: String, limit: Int = 50): Flux<UserProfile> {
    val like = "%$nameQuery%"
    return scatterGatherTemplate.scatterGather { shardName, _ ->
      userProfileRepository.searchByNameIlike(like, limit)
        .contextWrite(ShardContext.withShard(shardName))
    }
  }

  fun countsByShard(): Mono<List<ShardCount>> {
    return Flux.fromIterable(shardRegistry.allShardNames())
      .flatMap { shardName ->
        userProfileRepository.count()
          .contextWrite(ShardContext.withShard(shardName))
          .map { count -> ShardCount(shard = shardName, count = count) }
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

