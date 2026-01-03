package com.andver.client.notification.model.client

const val DOMAIN_CLIENT_EVENT_TOPIC = "domain.client.event"

data class DomainClientEvent(
  val type: String,
  val userId: Long,
  val payload: Map<String, Any?>?,
)
