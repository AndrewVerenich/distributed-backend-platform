package com.andver.counter.model

data class VideoViewEvent(
  val userId: Long,
  val videoId: Long,
  val timestamp: Long,
)
