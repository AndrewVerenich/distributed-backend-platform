package com.andver.webhook.processor

import com.andver.webhook.core.EventPayload
import com.andver.webhook.core.NotificationMessage
import com.andver.webhook.core.Topics
import com.andver.webhook.store.DeliveryAttemptRepository
import com.andver.webhook.store.WebhookRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class FanoutService(
  private val webhooks: WebhookRepository,
  private val attempts: DeliveryAttemptRepository,
  private val kafkaTemplate: KafkaTemplate<String, String>,
  private val objectMapper: ObjectMapper,
  registry: MeterRegistry,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val produced = Counter.builder("webhook_notifications_produced").register(registry)

  fun process(payloadJson: String): Mono<Void> {
    val payload = objectMapper.readValue(payloadJson, EventPayload::class.java)
    return webhooks.findActiveByTenantAndType(payload.tenantId, payload.eventType)
      .flatMap { webhook ->
        attempts.upsertPending(
          eventId = payload.eventId,
          webhookId = webhook.id,
          tenantId = payload.tenantId,
          partitionKey = payload.partitionKey,
          eventType = payload.eventType,
          body = payload.body,
        ).flatMap {
          val msg = NotificationMessage(
            eventId = payload.eventId,
            webhookId = webhook.id,
            tenantId = payload.tenantId,
            eventType = payload.eventType,
            partitionKey = payload.partitionKey,
            body = payload.body,
          )
          val key = "${payload.partitionKey}:${webhook.id}"
          val json = objectMapper.writeValueAsString(msg)
          Mono.fromFuture(
            kafkaTemplate.send(Topics.NOTIFICATION, key, json).toCompletableFuture(),
          ).doOnSuccess {
            produced.increment()
            log.info("notification produced eventId={} webhookId={}", payload.eventId, webhook.id)
          }
        }
      }
      .then()
  }
}
