package com.andver.time.starter.model

data class SyncResponse(
  val serverTime: Long,
  val roundTripTime: Long,
  val offset: Long
)

