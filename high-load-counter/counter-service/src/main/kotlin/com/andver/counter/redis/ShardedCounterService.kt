package com.andver.counter.redis

import com.andver.counter.metrics.CounterMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

interface ShardedCounterService {
  fun incrementViews(videoId: Long, delta: Long): Mono<Void>
  fun getTotalViews(videoId: Long): Mono<Long>
  fun addUniqueViewer(videoId: Long, userId: Long): Mono<Void>
  fun getUniqueViewers(videoId: Long): Mono<Long>
}

@Service
class DefaultShardedCounterService(
  private val redisTemplate: ReactiveStringRedisTemplate,
  private val metrics: CounterMetrics,
  @Value("\${counter.num-shards:8}") private val numShards: Int,
) : ShardedCounterService {

  override fun incrementViews(videoId: Long, delta: Long): Mono<Void> {
    val shard = ThreadLocalRandom.current().nextInt(numShards)
    val started = System.nanoTime()
    return redisTemplate.opsForValue()
      .increment(shardKey(videoId, shard), delta)
      .doOnSuccess {
        metrics.recordShardIncrement(shard, delta)
        metrics.recordRedisIncr(Duration.ofNanos(System.nanoTime() - started))
      }
      .then()
  }

  override fun getTotalViews(videoId: Long): Mono<Long> {
    val keys = (0 until numShards).map { shardKey(videoId, it) }
    val started = System.nanoTime()
    return redisTemplate.opsForValue().multiGet(keys)
      .map { values -> values?.sumOf { it?.toLongOrNull() ?: 0L } ?: 0L }
      .doOnSuccess {
        metrics.recordRedisMget(Duration.ofNanos(System.nanoTime() - started))
      }
  }

  override fun addUniqueViewer(videoId: Long, userId: Long): Mono<Void> {
    return redisTemplate.opsForHyperLogLog()
      .add(uniqueViewersKey(videoId), userId.toString())
      .doOnSuccess { metrics.recordHllAdd() }
      .then()
  }

  override fun getUniqueViewers(videoId: Long): Mono<Long> {
    val started = System.nanoTime()
    return redisTemplate.opsForHyperLogLog().size(uniqueViewersKey(videoId))
      .doOnSuccess {
        metrics.recordHllSize(Duration.ofNanos(System.nanoTime() - started))
      }
  }

  private fun shardKey(videoId: Long, shard: Int) = "video:$videoId:views:shard:$shard"

  private fun uniqueViewersKey(videoId: Long) = "video:$videoId:unique-viewers"
}
