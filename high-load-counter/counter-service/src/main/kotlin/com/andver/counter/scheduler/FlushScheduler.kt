package com.andver.counter.scheduler

import com.andver.counter.redis.ShardedCounterService
import com.andver.counter.repository.VideoCounterRepository
import com.andver.counter.tracker.VideoIdTracker
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

@Component
class FlushScheduler(
  private val shardedCounterService: ShardedCounterService,
  private val repository: VideoCounterRepository,
  private val videoIdTracker: VideoIdTracker,
) {

  private val logger = LoggerFactory.getLogger(FlushScheduler::class.java)

  @Scheduled(fixedDelayString = "\${counter.flush-interval-ms:30000}")
  fun flush() {
    val ids = videoIdTracker.trackedIds()
    if (ids.isEmpty()) return

    logger.info("Flushing {} video counters to database", ids.size)

    Flux.fromIterable(ids)
      .flatMap { videoId ->
        Mono.zip(
          shardedCounterService.getTotalViews(videoId),
          shardedCounterService.getUniqueViewers(videoId),
        ).flatMap { (totalViews, uniqueViewersCount) ->
          repository.upsert(videoId, totalViews, uniqueViewersCount)
            .doOnSuccess { videoIdTracker.untrack(videoId) }
        }
      }
      .then()
      .subscribe()
  }
}
