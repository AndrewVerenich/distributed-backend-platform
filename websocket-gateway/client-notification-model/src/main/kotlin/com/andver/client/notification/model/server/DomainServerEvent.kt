package com.andver.client.notification.model.server

const val DOMAIN_SERVER_EVENT_TOPIC = "domain.server.event"

data class DomainServerEvent<T>(
  val type: DomainServerEventType,
  val userId: Long,
  val payload: T?,
)