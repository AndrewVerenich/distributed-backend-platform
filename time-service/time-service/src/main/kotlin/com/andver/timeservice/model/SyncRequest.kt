package com.andver.timeservice.model

data class SyncRequest(
  val nodeId: String,
  val localTime: Long
)

