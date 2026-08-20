package com.andver.push.demo.api

data class PublishEventRequest(
  val clientId: String,
  val type: String = "demo.event",
  val payload: Map<String, Any?> = emptyMap(),
)

data class BurstEventRequest(
  val clientId: String,
  val count: Int = 100,
  val intervalMs: Long = 10,
  val type: String = "demo.burst",
)
