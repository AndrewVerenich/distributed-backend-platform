package com.andver.counter.model

data class VideoCounter(
  val videoId: Long,
  val totalViews: Long,
  val uniqueViewers: Long,
)
