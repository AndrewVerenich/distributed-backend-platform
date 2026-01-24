package com.andver.time.starter.service

import com.andver.time.starter.cache.OffsetCache
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

interface OffsetProvider {
  fun getOffset(): Long
  fun updateOffset(offset: Long)
}

class DefaultOffsetProvider(
  private val offsetCache: OffsetCache,
  private val nodeId: String
) : OffsetProvider {
  private val currentOffset = AtomicReference(0L)

  init {
    loadOffsetFromCache()
      .doOnSuccess { offset ->
        if (offset != null) {
          currentOffset.set(offset)
        }
      }
      .subscribe()
  }

  override fun getOffset(): Long {
    return currentOffset.get()
  }

  override fun updateOffset(offset: Long) {
    currentOffset.set(offset)
    offsetCache.saveOffset(nodeId, offset).subscribe()
  }

  private fun loadOffsetFromCache(): Mono<Long?> {
    return offsetCache.getOffset(nodeId)
  }
}

