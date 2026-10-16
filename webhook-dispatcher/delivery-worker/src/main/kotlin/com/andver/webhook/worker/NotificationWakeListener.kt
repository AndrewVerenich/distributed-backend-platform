package com.andver.webhook.worker

import com.andver.webhook.core.NotificationMessage
import com.andver.webhook.store.DeliveryAttemptRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer only ensures the delivery_attempts row exists — no outbound HTTP here.
 */
@Component
class NotificationWakeListener(
  private val attempts: DeliveryAttemptRepository,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  @KafkaListener(
    topics = ["\${webhook.topics.notification:webhook.notification}"],
    groupId = "webhook-delivery-wake",
  )
  fun onNotification(payload: String) {
    val msg = objectMapper.readValue(payload, NotificationMessage::class.java)
    attempts.upsertPending(
      eventId = msg.eventId,
      webhookId = msg.webhookId,
      tenantId = msg.tenantId,
      partitionKey = msg.partitionKey,
      eventType = msg.eventType,
      body = msg.body,
    ).block()
    log.debug("wake eventId={} webhookId={}", msg.eventId, msg.webhookId)
  }
}
