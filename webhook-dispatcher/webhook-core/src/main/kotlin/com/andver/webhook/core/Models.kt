package com.andver.webhook.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventPayload(
  val eventId: String,
  val tenantId: String,
  val eventType: String,
  val partitionKey: String,
  val body: JsonNode,
  val occurredAt: Instant? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotificationMessage(
  val eventId: String,
  val webhookId: String,
  val tenantId: String,
  val eventType: String,
  val partitionKey: String,
  val body: JsonNode,
)

enum class WebhookStatus {
  active,
  paused,
}

enum class DeliveryStatus {
  pending,
  in_flight,
  succeeded,
  retry,
  dlq,
}

sealed class DeliveryOutcome {
  data object Success : DeliveryOutcome()
  data class Retryable(val reason: String) : DeliveryOutcome()
  data class NonRetryable(val statusCode: Int, val reason: String) : DeliveryOutcome()
}
