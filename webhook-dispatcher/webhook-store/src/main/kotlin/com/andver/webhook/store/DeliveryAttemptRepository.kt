package com.andver.webhook.store

import com.andver.webhook.core.DeliveryStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class DeliveryAttemptRepository(
  private val db: DatabaseClient,
  private val objectMapper: ObjectMapper,
) {
  fun upsertPending(
    eventId: String,
    webhookId: String,
    tenantId: String,
    partitionKey: String,
    eventType: String,
    body: com.fasterxml.jackson.databind.JsonNode,
  ): Mono<DeliveryAttempt> {
    val id = "da_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val idempotencyKey = "$eventId:$webhookId"
    val bodyJson = objectMapper.writeValueAsString(body)
    return db.sql(
      """
      INSERT INTO delivery_attempts (
        id, event_id, webhook_id, tenant_id, partition_key, event_type, body,
        status, attempt_count, next_attempt_at, idempotency_key
      ) VALUES (
        :id, :eventId, :webhookId, :tenantId, :partitionKey, :eventType, :body::jsonb,
        'pending', 0, now(), :idempotencyKey
      )
      ON CONFLICT (event_id, webhook_id) DO UPDATE
        SET updated_at = now()
      RETURNING id, event_id, webhook_id, tenant_id, partition_key, event_type, body::text AS body,
                status, attempt_count, next_attempt_at, claimed_at, last_error, idempotency_key,
                created_at, updated_at
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("eventId", eventId)
      .bind("webhookId", webhookId)
      .bind("tenantId", tenantId)
      .bind("partitionKey", partitionKey)
      .bind("eventType", eventType)
      .bind("body", bodyJson)
      .bind("idempotencyKey", idempotencyKey)
      .map { row, _ -> mapAttempt(row) }
      .one()
  }

  /**
   * Claim one due attempt with SKIP LOCKED.
   * Gates: webhook active, in-flight cap, ordering within partition_key+webhook_id.
   */
  fun claimOne(maxInFlightPerWebhook: Int): Mono<DeliveryAttempt> =
    db.sql(
      """
      UPDATE delivery_attempts AS a
         SET status = 'in_flight',
             claimed_at = now(),
             updated_at = now()
       WHERE a.id = (
         SELECT a2.id
           FROM delivery_attempts a2
           JOIN webhooks w ON w.id = a2.webhook_id
          WHERE a2.status IN ('pending', 'retry')
            AND a2.next_attempt_at <= now()
            AND w.status = 'active'
            AND (
              SELECT count(*) FROM delivery_attempts x
               WHERE x.webhook_id = a2.webhook_id AND x.status = 'in_flight'
            ) < :maxInFlight
            AND NOT EXISTS (
              SELECT 1 FROM delivery_attempts earlier
               WHERE earlier.partition_key = a2.partition_key
                 AND earlier.webhook_id = a2.webhook_id
                 AND earlier.created_at < a2.created_at
                 AND earlier.status IN ('pending', 'in_flight', 'retry')
            )
          ORDER BY a2.next_attempt_at, a2.created_at
          LIMIT 1
          FOR UPDATE OF a2 SKIP LOCKED
       )
       RETURNING id, event_id, webhook_id, tenant_id, partition_key, event_type, body::text AS body,
                 status, attempt_count, next_attempt_at, claimed_at, last_error, idempotency_key,
                 created_at, updated_at
      """.trimIndent(),
    )
      .bind("maxInFlight", maxInFlightPerWebhook)
      .map { row, _ -> mapAttempt(row) }
      .one()

  fun markSucceeded(id: String): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'succeeded', claimed_at = NULL, last_error = NULL, updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .fetch()
      .rowsUpdated()

  fun markRetry(id: String, attemptCount: Int, nextAttemptAt: Instant, error: String): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'retry',
             attempt_count = :attemptCount,
             next_attempt_at = :nextAt,
             claimed_at = NULL,
             last_error = :error,
             updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("attemptCount", attemptCount)
      .bind("nextAt", nextAttemptAt.atOffset(ZoneOffset.UTC))
      .bind("error", error.take(1000))
      .fetch()
      .rowsUpdated()

  fun markDlq(id: String, attemptCount: Int, error: String): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'dlq',
             attempt_count = :attemptCount,
             claimed_at = NULL,
             last_error = :error,
             updated_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("attemptCount", attemptCount)
      .bind("error", error.take(1000))
      .fetch()
      .rowsUpdated()

  fun reclaimStale(leaseSeconds: Long): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'retry',
             next_attempt_at = now(),
             claimed_at = NULL,
             last_error = 'lease expired',
             updated_at = now()
       WHERE status = 'in_flight'
         AND claimed_at < now() - make_interval(secs => :leaseSeconds)
      """.trimIndent(),
    )
      .bind("leaseSeconds", leaseSeconds)
      .fetch()
      .rowsUpdated()

  fun replay(id: String): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'pending',
             next_attempt_at = now(),
             claimed_at = NULL,
             last_error = NULL,
             updated_at = now()
       WHERE id = :id AND status = 'dlq'
      """.trimIndent(),
    )
      .bind("id", id)
      .fetch()
      .rowsUpdated()

  fun replayDlqForWebhook(webhookId: String): Mono<Long> =
    db.sql(
      """
      UPDATE delivery_attempts
         SET status = 'pending',
             next_attempt_at = now(),
             claimed_at = NULL,
             last_error = NULL,
             updated_at = now()
       WHERE webhook_id = :webhookId AND status = 'dlq'
      """.trimIndent(),
    )
      .bind("webhookId", webhookId)
      .fetch()
      .rowsUpdated()

  fun findByEventId(eventId: String): Flux<DeliveryAttempt> =
    db.sql(
      """
      SELECT id, event_id, webhook_id, tenant_id, partition_key, event_type, body::text AS body,
             status, attempt_count, next_attempt_at, claimed_at, last_error, idempotency_key,
             created_at, updated_at
        FROM delivery_attempts
       WHERE event_id = :eventId
       ORDER BY created_at
      """.trimIndent(),
    )
      .bind("eventId", eventId)
      .map { row, _ -> mapAttempt(row) }
      .all()

  fun findById(id: String): Mono<DeliveryAttempt> =
    db.sql(
      """
      SELECT id, event_id, webhook_id, tenant_id, partition_key, event_type, body::text AS body,
             status, attempt_count, next_attempt_at, claimed_at, last_error, idempotency_key,
             created_at, updated_at
        FROM delivery_attempts
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .map { row, _ -> mapAttempt(row) }
      .one()

  private fun mapAttempt(row: io.r2dbc.spi.Readable): DeliveryAttempt {
    val bodyJson = row.get("body", String::class.java)!!
    return DeliveryAttempt(
      id = row.get("id", String::class.java)!!,
      eventId = row.get("event_id", String::class.java)!!,
      webhookId = row.get("webhook_id", String::class.java)!!,
      tenantId = row.get("tenant_id", String::class.java)!!,
      partitionKey = row.get("partition_key", String::class.java)!!,
      eventType = row.get("event_type", String::class.java)!!,
      body = objectMapper.readTree(bodyJson),
      status = DeliveryStatus.valueOf(row.get("status", String::class.java)!!),
      attemptCount = row.get("attempt_count", Integer::class.java)!!.toInt(),
      nextAttemptAt = row.get("next_attempt_at", OffsetDateTime::class.java)!!.toInstant(),
      claimedAt = row.get("claimed_at", OffsetDateTime::class.java)?.toInstant(),
      lastError = row.get("last_error", String::class.java),
      idempotencyKey = row.get("idempotency_key", String::class.java)!!,
      createdAt = row.get("created_at", OffsetDateTime::class.java)!!.toInstant(),
      updatedAt = row.get("updated_at", OffsetDateTime::class.java)!!.toInstant(),
    )
  }
}
