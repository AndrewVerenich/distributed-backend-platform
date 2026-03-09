package com.andver.counter.service

import com.andver.counter.model.VideoCounter
import com.andver.counter.redis.ShardedCounterService
import com.andver.counter.tracker.VideoIdTracker
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

interface CounterService {
  fun getCounter(videoId: Long): Mono<VideoCounter>
  fun recordView(videoId: Long, userId: Long): Mono<Void>
  fun incrementViewCount(videoId: Long, delta: Long): Mono<Void>
  fun trackUniqueViewer(videoId: Long, userId: Long): Mono<Void>
}

@Service
class DefaultCounterService(
  private val shardedCounterService: ShardedCounterService,
  private val videoIdTracker: VideoIdTracker,
) : CounterService {

  override fun getCounter(videoId: Long): Mono<VideoCounter> {
    return Mono.zip(
      shardedCounterService.getTotalViews(videoId),
      shardedCounterService.getUniqueViewers(videoId),
    ).map { (totalViews, uniqueViewers) -> VideoCounter(videoId, totalViews, uniqueViewers) }
  }

  override fun recordView(videoId: Long, userId: Long): Mono<Void> {
    videoIdTracker.track(videoId)
    return shardedCounterService.incrementViews(videoId, 1L)
      .then(shardedCounterService.addUniqueViewer(videoId, userId))
  }

  override fun incrementViewCount(videoId: Long, delta: Long): Mono<Void> {
    videoIdTracker.track(videoId)
    return shardedCounterService.incrementViews(videoId, delta)
  }

  override fun trackUniqueViewer(videoId: Long, userId: Long): Mono<Void> {
    return shardedCounterService.addUniqueViewer(videoId, userId)
  }
}
