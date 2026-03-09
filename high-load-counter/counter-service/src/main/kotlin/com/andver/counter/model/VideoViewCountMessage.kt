package com.andver.counter.model

data class VideoViewCountMessage(
  val videoId: Long,
  val count: Long,
  val windowStart: Long,
  val windowEnd: Long,
)
