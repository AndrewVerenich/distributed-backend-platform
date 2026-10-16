package com.andver.webhook.api

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class CreateWebhookRequest(
  val eventType: String,
  val url: String,
)

data class WebhookResponse(
  val id: String,
  val eventType: String,
  val url: String,
  val status: String,
  val signingSecret: String? = null,
  val previousSecretValidUntil: Instant? = null,
)

data class AcceptEventRequest(
  val eventId: String,
  val tenantId: String,
  val eventType: String,
  val partitionKey: String,
  val body: JsonNode,
  val occurredAt: Instant? = null,
)

data class AcceptEventResponse(
  val eventId: String,
  val status: String,
)

data class DeliveryResponse(
  val id: String,
  val eventId: String,
  val webhookId: String,
  val status: String,
  val attemptCount: Int,
  val lastError: String?,
  val idempotencyKey: String,
  val updatedAt: Instant,
)

data class RotateSecretResponse(
  val id: String,
  val signingSecret: String,
  val previousSecretValidUntil: Instant,
)

data class CountResponse(val updated: Long)
