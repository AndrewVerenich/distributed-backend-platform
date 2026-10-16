package com.andver.webhook.store

import com.andver.webhook.core.WebhookStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class WebhookRepository(
  private val db: DatabaseClient,
) {
  fun insert(webhook: Webhook): Mono<Webhook> =
    db.sql(
      """
      INSERT INTO webhooks (
        id, tenant_id, event_type, url, signing_secret, status,
        consecutive_transport_failures, created_at, updated_at
      ) VALUES (
        :id, :tenantId, :eventType, :url, :secret, :status,
        0, :createdAt, :updatedAt
      )
      """.trimIndent(),
    )
      .bind("id", webhook.id)
      .bind("tenantId", webhook.tenantId)
      .bind("eventType", webhook.eventType)
      .bind("url", webhook.url)
      .bind("secret", webhook.signingSecret)
      .bind("status", webhook.status.name)
      .bind("createdAt", webhook.createdAt.atOffset(ZoneOffset.UTC))
      .bind("updatedAt", webhook.updatedAt.atOffset(ZoneOffset.UTC))
      .fetch()
      .rowsUpdated()
      .thenReturn(webhook)

  fun findById(id: String): Mono<Webhook> =
    db.sql("SELECT * FROM webhooks WHERE id = :id")
      .bind("id", id)
      .map { row, _ -> mapWebhook(row) }
      .one()

  fun findByTenant(tenantId: String): Flux<Webhook> =
    db.sql("SELECT * FROM webhooks WHERE tenant_id = :tenantId ORDER BY created_at")
      .bind("tenantId", tenantId)
      .map { row, _ -> mapWebhook(row) }
      .all()

  fun findActiveByTenantAndType(tenantId: String, eventType: String): Flux<Webhook> =
    db.sql(
      """
      SELECT * FROM webhooks
       WHERE tenant_id = :tenantId
         AND event_type = :eventType
         AND status = 'active'
      """.trimIndent(),
    )
      .bind("tenantId", tenantId)
      .bind("eventType", eventType)
      .map { row, _ -> mapWebhook(row) }
      .all()

  fun delete(id: String, tenantId: String): Mono<Long> =
    db.sql("DELETE FROM webhooks WHERE id = :id AND tenant_id = :tenantId")
      .bind("id", id)
      .bind("tenantId", tenantId)
      .fetch()
      .rowsUpdated()

  fun resume(id: String): Mono<Long> =
    db.sql(
      """
      UPDATE webhooks
         SET status = 'active',
             consecutive_transport_failures = 0,
             updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .fetch()
      .rowsUpdated()

  fun pause(id: String): Mono<Long> =
    db.sql(
      """
      UPDATE webhooks
         SET status = 'paused', updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .fetch()
      .rowsUpdated()

  fun rotateSecret(
    id: String,
    newSecret: String,
    previousValidUntil: Instant,
  ): Mono<Long> =
    db.sql(
      """
      UPDATE webhooks
         SET previous_signing_secret = signing_secret,
             previous_secret_valid_until = :until,
             signing_secret = :newSecret,
             updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("newSecret", newSecret)
      .bind("until", previousValidUntil.atOffset(ZoneOffset.UTC))
      .fetch()
      .rowsUpdated()

  fun incrementTransportFailure(id: String, pauseAfter: Int): Mono<Webhook> =
    db.sql(
      """
      UPDATE webhooks
         SET consecutive_transport_failures = consecutive_transport_failures + 1,
             status = CASE
               WHEN consecutive_transport_failures + 1 >= :pauseAfter THEN 'paused'
               ELSE status
             END,
             updated_at = now()
       WHERE id = :id
       RETURNING *
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("pauseAfter", pauseAfter)
      .map { row, _ -> mapWebhook(row) }
      .one()

  fun resetTransportFailures(id: String): Mono<Long> =
    db.sql(
      """
      UPDATE webhooks
         SET consecutive_transport_failures = 0, updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .fetch()
      .rowsUpdated()

  private fun mapWebhook(row: io.r2dbc.spi.Readable): Webhook {
    val prevUntil = row.get("previous_secret_valid_until", OffsetDateTime::class.java)
    return Webhook(
      id = row.get("id", String::class.java)!!,
      tenantId = row.get("tenant_id", String::class.java)!!,
      eventType = row.get("event_type", String::class.java)!!,
      url = row.get("url", String::class.java)!!,
      signingSecret = row.get("signing_secret", String::class.java)!!,
      previousSigningSecret = row.get("previous_signing_secret", String::class.java),
      previousSecretValidUntil = prevUntil?.toInstant(),
      status = WebhookStatus.valueOf(row.get("status", String::class.java)!!),
      consecutiveTransportFailures = row.get("consecutive_transport_failures", Integer::class.java)!!.toInt(),
      createdAt = row.get("created_at", OffsetDateTime::class.java)!!.toInstant(),
      updatedAt = row.get("updated_at", OffsetDateTime::class.java)!!.toInstant(),
    )
  }
}
