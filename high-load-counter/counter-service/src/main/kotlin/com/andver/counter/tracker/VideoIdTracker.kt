package com.andver.counter.tracker

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface VideoIdTracker {
  fun track(videoId: Long)
  fun untrack(videoId: Long)
  fun trackedIds(): Set<Long>
}

@Component
class DefaultVideoIdTracker : VideoIdTracker {

  private val videoIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

  override fun track(videoId: Long) {
    videoIds.add(videoId)
  }

  override fun untrack(videoId: Long) {
    videoIds.remove(videoId)
  }

  override fun trackedIds(): Set<Long> {
    return videoIds
  }
}
