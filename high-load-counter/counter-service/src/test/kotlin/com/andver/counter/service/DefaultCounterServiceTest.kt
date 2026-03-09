package com.andver.counter.service

import com.andver.counter.model.VideoCounter
import com.andver.counter.redis.ShardedCounterService
import com.andver.counter.tracker.DefaultVideoIdTracker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class DefaultCounterServiceTest {

  private val shardedCounterService = mockk<ShardedCounterService>()
  private val videoIdTracker = DefaultVideoIdTracker()
  private lateinit var counterService: DefaultCounterService

  @BeforeEach
  fun setUp() {
    counterService = DefaultCounterService(shardedCounterService, videoIdTracker)
  }

  @Test
  fun `getCounter combines total views and unique viewers from Redis`() {
    every { shardedCounterService.getTotalViews(1L) } returns Mono.just(1500L)
    every { shardedCounterService.getUniqueViewers(1L) } returns Mono.just(320L)

    StepVerifier.create(counterService.getCounter(1L))
      .assertNext { counter ->
        assertThat(counter).isEqualTo(VideoCounter(videoId = 1L, totalViews = 1500L, uniqueViewers = 320L))
      }
      .verifyComplete()
  }

  @Test
  fun `getCounter returns zeros for unknown video`() {
    every { shardedCounterService.getTotalViews(999L) } returns Mono.just(0L)
    every { shardedCounterService.getUniqueViewers(999L) } returns Mono.just(0L)

    StepVerifier.create(counterService.getCounter(999L))
      .assertNext { counter ->
        assertThat(counter.totalViews).isZero()
        assertThat(counter.uniqueViewers).isZero()
      }
      .verifyComplete()
  }

  @Test
  fun `recordView increments shard and adds to HyperLogLog`() {
    every { shardedCounterService.incrementViews(42L, 1L) } returns Mono.empty()
    every { shardedCounterService.addUniqueViewer(42L, 7L) } returns Mono.empty()

    StepVerifier.create(counterService.recordView(videoId = 42L, userId = 7L))
      .verifyComplete()

    verify(exactly = 1) { shardedCounterService.incrementViews(42L, 1L) }
    verify(exactly = 1) { shardedCounterService.addUniqueViewer(42L, 7L) }
  }

  @Test
  fun `recordView tracks videoId for flush scheduler`() {
    every { shardedCounterService.incrementViews(55L, 1L) } returns Mono.empty()
    every { shardedCounterService.addUniqueViewer(55L, 1L) } returns Mono.empty()

    counterService.recordView(videoId = 55L, userId = 1L).block()

    assertThat(videoIdTracker.trackedIds()).contains(55L)
  }

  @Test
  fun `incrementViewCount adds batch delta to sharded counter`() {
    every { shardedCounterService.incrementViews(10L, 250L) } returns Mono.empty()

    StepVerifier.create(counterService.incrementViewCount(videoId = 10L, delta = 250L))
      .verifyComplete()

    verify(exactly = 1) { shardedCounterService.incrementViews(10L, 250L) }
    verify(exactly = 0) { shardedCounterService.addUniqueViewer(any(), any()) }
  }

  @Test
  fun `trackUniqueViewer only updates HyperLogLog without touching view counter`() {
    every { shardedCounterService.addUniqueViewer(10L, 99L) } returns Mono.empty()

    StepVerifier.create(counterService.trackUniqueViewer(videoId = 10L, userId = 99L))
      .verifyComplete()

    verify(exactly = 1) { shardedCounterService.addUniqueViewer(10L, 99L) }
    verify(exactly = 0) { shardedCounterService.incrementViews(any(), any()) }
  }
}
