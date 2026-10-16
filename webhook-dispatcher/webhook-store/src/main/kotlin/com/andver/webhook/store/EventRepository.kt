package com.andver.webhook.store

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class EventRepository(
  private val db: DatabaseClient,
  private val objectMapper: ObjectMapper,
) {
  /**
   * Insert event. On conflict (same eventId) returns existing row without error.
   */
  fun insertIdempotent(
    eventId: String,
    tenantId: String,
    eventType: String,
    partitionKey: String,
    payload: JsonNode,
  ): Mono<InsertResult> {
    val payloadJson = objectMapper.writeValueAsString(payload)
    return db.sql(
      """
      INSERT INTO events (event_id, tenant_id, event_type, partitioning_key, type, payload)
      VALUES (:eventId, :tenantId, :eventType, :partitionKey, 'event', :payload::jsonb)
      ON CONFLICT (event_id) DO NOTHING
      RETURNING id::text AS id, event_id, true AS inserted
      """.trimIndent(),
    )
      .bind("eventId", eventId)
      .bind("tenantId", tenantId)
      .bind("eventType", eventType)
      .bind("partitionKey", partitionKey)
      .bind("payload", payloadJson)
      .map { row, _ ->
        InsertResult(
          eventId = row.get("event_id", String::class.java)!!,
          inserted = true,
        )
      }
      .one()
      .switchIfEmpty(
        db.sql("SELECT event_id FROM events WHERE event_id = :eventId")
          .bind("eventId", eventId)
          .map { row, _ ->
            InsertResult(
              eventId = row.get("event_id", String::class.java)!!,
              inserted = false,
            )
          }
          .one(),
      )
  }

  fun findByEventId(eventId: String): Mono<StoredEvent> =
    db.sql("SELECT id::text AS id, event_id, tenant_id, event_type, partitioning_key, payload, created_at FROM events WHERE event_id = :eventId")
      .bind("eventId", eventId)
      .map { row, _ ->
        val payloadStr = row.get("payload", String::class.java)
          ?: objectMapper.writeValueAsString(row.get("payload"))
        StoredEvent(
          id = row.get("id", String::class.java)!!,
          eventId = row.get("event_id", String::class.java)!!,
          tenantId = row.get("tenant_id", String::class.java)!!,
          eventType = row.get("event_type", String::class.java)!!,
          partitionKey = row.get("partitioning_key", String::class.java)!!,
          payload = objectMapper.readTree(payloadStr),
          createdAt = row.get("created_at", OffsetDateTime::class.java)!!.toInstant(),
        )
      }
      .one()

  data class InsertResult(val eventId: String, val inserted: Boolean)
}
