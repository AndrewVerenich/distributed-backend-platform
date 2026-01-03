package com.andver.gateway.client.notification.model

data class InternalDomainEvent(
  val type: String,
  val userId: Long,
  val payload: Map<String, Any?>?,
)
