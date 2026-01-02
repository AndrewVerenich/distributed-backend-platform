package com.andver.gateway.websocket.model

data class InternalClientWebSocketEvent(
  val target: String,
  val type: String,
  var userId: Long? = null,
  val payload: Map<String, Any?>?,
)
