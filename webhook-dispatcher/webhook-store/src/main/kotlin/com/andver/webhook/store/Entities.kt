package com.andver.webhook.store

import com.andver.webhook.core.DeliveryStatus
import com.andver.webhook.core.WebhookStatus
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class Tenant(
  val id: String,
  val apiKey: String,
  val name: String,
)

data class Webhook(
  val id: String,
  val tenantId: String,
  val eventType: String,
  val url: String,
  val signingSecret: String,
  val previousSigningSecret: String?,
  val previousSecretValidUntil: Instant?,
  val status: WebhookStatus,
  val consecutiveTransportFailures: Int,
  val createdAt: Instant,
  val updatedAt: Instant,
)

data class StoredEvent(
  val id: String,
  val eventId: String,
  val tenantId: String,
  val eventType: String,
  val partitionKey: String,
  val payload: JsonNode,
  val createdAt: Instant,
)

data class DeliveryAttempt(
  val id: String,
  val eventId: String,
  val webhookId: String,
  val tenantId: String,
  val partitionKey: String,
  val eventType: String,
  val body: JsonNode,
  val status: DeliveryStatus,
  val attemptCount: Int,
  val nextAttemptAt: Instant,
  val claimedAt: Instant?,
  val lastError: String?,
  val idempotencyKey: String,
  val createdAt: Instant,
  val updatedAt: Instant,
)
