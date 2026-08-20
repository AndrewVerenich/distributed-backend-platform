package com.andver.push.model

import java.time.Instant

const val PUSH_SERVER_EVENT_TOPIC = "push.server.event"
const val PUSH_CHANNEL_PREFIX = "PUSH_CHANNEL"
const val PUSH_REPLAY_PREFIX = "PUSH_REPLAY"
const val PUSH_EVENT_SEQ_KEY = "push:event:seq"
const val PUSH_REPLAY_MAX_SIZE = 100L

data class PushEvent(
  val eventId: Long = 0L,
  val clientId: String,
  val type: String,
  val payload: Map<String, Any?> = emptyMap(),
  val publishedAt: Instant = Instant.now(),
)
