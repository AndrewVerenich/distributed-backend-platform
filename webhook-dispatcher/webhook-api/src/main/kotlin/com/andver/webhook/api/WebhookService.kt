package com.andver.webhook.api

import com.andver.webhook.core.SsrfGuard
import com.andver.webhook.core.WebhookStatus
import com.andver.webhook.store.DeliveryAttemptRepository
import com.andver.webhook.store.EventRepository
import com.andver.webhook.store.TenantRepository
import com.andver.webhook.store.Webhook
import com.andver.webhook.store.WebhookRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class WebhookService(
  private val tenants: TenantRepository,
  private val webhooks: WebhookRepository,
  private val events: EventRepository,
  private val deliveries: DeliveryAttemptRepository,
  private val objectMapper: ObjectMapper,
  private val metrics: WebhookMetrics,
  @Value("\${webhook.allow-private-urls:true}") private val allowPrivateUrls: Boolean,
  @Value("\${webhook.secret-grace:1h}") private val secretGrace: Duration,
) {
  private val random = SecureRandom()

  fun requireTenant(apiKey: String?): Mono<com.andver.webhook.store.Tenant> {
    if (apiKey.isNullOrBlank()) {
      return Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "X-Api-Key required"))
    }
    return tenants.findByApiKey(apiKey)
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid api key")))
  }

  fun createWebhook(tenantId: String, req: CreateWebhookRequest): Mono<WebhookResponse> {
    SsrfGuard.validate(req.url, allowPrivateUrls).getOrElse {
      return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, it.message))
    }
    val now = Instant.now()
    val secret = randomSecret()
    val webhook = Webhook(
      id = "wh_" + UUID.randomUUID().toString().replace("-", "").take(12),
      tenantId = tenantId,
      eventType = req.eventType,
      url = req.url,
      signingSecret = secret,
      previousSigningSecret = null,
      previousSecretValidUntil = null,
      status = WebhookStatus.active,
      consecutiveTransportFailures = 0,
      createdAt = now,
      updatedAt = now,
    )
    return webhooks.insert(webhook)
      .doOnSuccess { metrics.webhookCreated() }
      .map {
        WebhookResponse(
          id = it.id,
          eventType = it.eventType,
          url = it.url,
          status = it.status.name,
          signingSecret = it.signingSecret,
        )
      }
  }

  fun listWebhooks(tenantId: String): Flux<WebhookResponse> =
    webhooks.findByTenant(tenantId).map { it.toPublic() }

  fun getWebhook(tenantId: String, id: String): Mono<WebhookResponse> =
    webhooks.findById(id)
      .filter { it.tenantId == tenantId }
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
      .map { it.toPublic() }

  fun deleteWebhook(tenantId: String, id: String): Mono<Void> =
    webhooks.delete(id, tenantId)
      .flatMap { n ->
        if (n == 0L) Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND))
        else Mono.empty()
      }

  fun acceptEvent(req: AcceptEventRequest): Mono<Pair<AcceptEventResponse, HttpStatus>> {
    val payload = objectMapper.createObjectNode().apply {
      put("eventId", req.eventId)
      put("tenantId", req.tenantId)
      put("eventType", req.eventType)
      put("partitionKey", req.partitionKey)
      set<ObjectNode>("body", req.body as? ObjectNode ?: objectMapper.valueToTree(req.body))
      put("occurredAt", (req.occurredAt ?: Instant.now()).toString())
    }
    return events.insertIdempotent(
      eventId = req.eventId,
      tenantId = req.tenantId,
      eventType = req.eventType,
      partitionKey = req.partitionKey,
      payload = payload,
    ).map { result ->
      if (result.inserted) {
        metrics.eventAccepted()
        AcceptEventResponse(result.eventId, "accepted") to HttpStatus.ACCEPTED
      } else {
        metrics.eventDuplicate()
        AcceptEventResponse(result.eventId, "duplicate") to HttpStatus.OK
      }
    }
  }

  fun resume(tenantId: String, id: String): Mono<WebhookResponse> =
    webhooks.findById(id)
      .filter { it.tenantId == tenantId }
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
      .flatMap { webhooks.resume(id).then(webhooks.findById(id)) }
      .map { it.toPublic() }

  fun rotateSecret(tenantId: String, id: String): Mono<RotateSecretResponse> =
    webhooks.findById(id)
      .filter { it.tenantId == tenantId }
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
      .flatMap {
        val newSecret = randomSecret()
        val until = Instant.now().plus(secretGrace)
        webhooks.rotateSecret(id, newSecret, until)
          .thenReturn(RotateSecretResponse(id, newSecret, until))
      }

  fun replayDelivery(id: String): Mono<CountResponse> =
    deliveries.replay(id).map { CountResponse(it) }

  fun replayDlq(tenantId: String, webhookId: String): Mono<CountResponse> =
    webhooks.findById(webhookId)
      .filter { it.tenantId == tenantId }
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))
      .flatMap { deliveries.replayDlqForWebhook(webhookId) }
      .map { CountResponse(it) }

  fun listDeliveries(eventId: String): Flux<DeliveryResponse> =
    deliveries.findByEventId(eventId).map {
      DeliveryResponse(
        id = it.id,
        eventId = it.eventId,
        webhookId = it.webhookId,
        status = it.status.name,
        attemptCount = it.attemptCount,
        lastError = it.lastError,
        idempotencyKey = it.idempotencyKey,
        updatedAt = it.updatedAt,
      )
    }

  private fun Webhook.toPublic() = WebhookResponse(
    id = id,
    eventType = eventType,
    url = url,
    status = status.name,
    previousSecretValidUntil = previousSecretValidUntil,
  )

  private fun randomSecret(): String {
    val bytes = ByteArray(24)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }
}
