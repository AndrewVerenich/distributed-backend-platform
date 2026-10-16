package com.andver.booking.repository

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class CompensationOutboxRepository(
  private val databaseClient: DatabaseClient,
) {

  fun insert(reservationId: String, payload: String): Mono<String> {
    val id = UUID.randomUUID().toString()
    return databaseClient.sql(
      """
      INSERT INTO compensation_outbox (id, reservation_id, payload, created_at)
      VALUES (:id, :reservationId, :payload, now())
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("reservationId", reservationId)
      .bind("payload", payload)
      .fetch()
      .rowsUpdated()
      .thenReturn(id)
  }

  fun claimUnpublished(limit: Int = 50): Flux<OutboxRow> =
    databaseClient.sql(
      """
      UPDATE compensation_outbox
         SET published_at = now()
       WHERE id IN (
         SELECT id FROM compensation_outbox
          WHERE published_at IS NULL
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
       )
      RETURNING id, reservation_id, payload
      """.trimIndent(),
    )
      .bind("limit", limit)
      .map { row, _ ->
        OutboxRow(
          id = row.get("id", String::class.java)!!,
          reservationId = row.get("reservation_id", String::class.java)!!,
          payload = row.get("payload", String::class.java)!!,
        )
      }
      .all()

  data class OutboxRow(val id: String, val reservationId: String, val payload: String)
}
