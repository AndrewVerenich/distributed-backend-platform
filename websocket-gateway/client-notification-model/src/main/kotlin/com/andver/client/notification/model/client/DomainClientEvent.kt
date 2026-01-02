package com.andver.client.notification.model.client

const val DOMAIN_CLIENT_EVENT_TOPIC = "domain.client.event"

data class DomainClientEvent<T>(
  val type: DomainClientEventType,
  val userId: Long,
  val payload: T?,
)
