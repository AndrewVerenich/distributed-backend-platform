package com.andver.timeservice.model

data class SyncResponse(
  val serverTime: Long,
  val roundTripTime: Long,
  val offset: Long
)

