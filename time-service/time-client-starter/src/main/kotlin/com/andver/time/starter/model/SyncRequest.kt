package com.andver.time.starter.model

data class SyncRequest(
  val nodeId: String,
  val localTime: Long
)