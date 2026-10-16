package com.andver.idempotency.server.repository

import com.andver.idempotency.api.IdempotencyStatus
import io.r2dbc.spi.Readable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.bind
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class IdempotencyRepository(
  private val databaseClient: DatabaseClient,
) {

  fun find(owner: String, key: String): Mono<IdempotencyRecord> =
    databaseClient.sql(
      """
      SELECT owner_id, idempotency_key, body_hash, status, lease_until, request_id,
             response_status, response_content_type, response_body,
             created_at, completed_at, expires_at
        FROM idempotency_records
       WHERE owner_id = :owner AND idempotency_key = :key
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .map { row, _ -> mapRow(row) }
      .one()

  fun tryInsertStarted(
    owner: String,
    key: String,
    bodyHash: String,
    requestId: String,
    leaseUntil: Instant,
  ): Mono<IdempotencyRecord> =
    databaseClient.sql(
      """
      INSERT INTO idempotency_records (
        owner_id, idempotency_key, body_hash, status, lease_until, request_id, created_at
      ) VALUES (
        :owner, :key, :bodyHash, 'started', :leaseUntil, :requestId, now()
      )
      ON CONFLICT (owner_id, idempotency_key) DO NOTHING
      RETURNING owner_id, idempotency_key, body_hash, status, lease_until, request_id,
                response_status, response_content_type, response_body,
                created_at, completed_at, expires_at
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .bind("bodyHash", bodyHash)
      .bind("requestId", requestId)
      .bind("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
      .map { row, _ -> mapRow(row) }
      .one()

  fun takeOverExpiredLease(
    owner: String,
    key: String,
    bodyHash: String,
    requestId: String,
    leaseUntil: Instant,
  ): Mono<IdempotencyRecord> =
    databaseClient.sql(
      """
      UPDATE idempotency_records
         SET body_hash = :bodyHash,
             request_id = :requestId,
             lease_until = :leaseUntil,
             response_status = NULL,
             response_content_type = NULL,
             response_body = NULL,
             completed_at = NULL,
             expires_at = NULL
       WHERE owner_id = :owner
         AND idempotency_key = :key
         AND status = 'started'
         AND lease_until < now()
      RETURNING owner_id, idempotency_key, body_hash, status, lease_until, request_id,
                response_status, response_content_type, response_body,
                created_at, completed_at, expires_at
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .bind("bodyHash", bodyHash)
      .bind("requestId", requestId)
      .bind("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
      .map { row, _ -> mapRow(row) }
      .one()

  fun complete(
    owner: String,
    key: String,
    requestId: String,
    responseStatus: Int,
    responseContentType: String?,
    responseBody: ByteArray?,
    expiresAt: Instant,
  ): Mono<Boolean> {
    var spec = databaseClient.sql(
      """
      UPDATE idempotency_records
         SET status = 'completed',
             response_status = :responseStatus,
             response_content_type = :responseContentType,
             response_body = :responseBody,
             completed_at = now(),
             expires_at = :expiresAt,
             lease_until = now()
       WHERE owner_id = :owner
         AND idempotency_key = :key
         AND request_id = :requestId
         AND status = 'started'
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .bind("requestId", requestId)
      .bind("responseStatus", responseStatus)
      .bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
    spec = if (responseContentType == null) {
      spec.bindNull("responseContentType", String::class.java)
    } else {
      spec.bind("responseContentType", responseContentType)
    }
    spec = if (responseBody == null) {
      spec.bindNull("responseBody", ByteArray::class.java)
    } else {
      spec.bind("responseBody", responseBody)
    }
    return spec.fetch().rowsUpdated().map { it > 0 }
  }

  private fun mapRow(row: Readable): IdempotencyRecord =
    IdempotencyRecord(
      owner = row.get("owner_id", String::class.java)!!,
      key = row.get("idempotency_key", String::class.java)!!,
      bodyHash = row.get("body_hash", String::class.java)!!,
      status = IdempotencyStatus.fromDb(row.get("status", String::class.java)!!),
      leaseUntil = toInstant(row.get("lease_until")),
      requestId = row.get("request_id", String::class.java)!!,
      responseStatus = row.get("response_status", Integer::class.java)?.toInt(),
      responseContentType = row.get("response_content_type", String::class.java),
      responseBody = toBytes(row.get("response_body")),
      createdAt = toInstant(row.get("created_at")),
      completedAt = row.get("completed_at")?.let { toInstant(it) },
      expiresAt = row.get("expires_at")?.let { toInstant(it) },
    )

  private fun toInstant(value: Any?): Instant =
    when (value) {
      is Instant -> value
      is OffsetDateTime -> value.toInstant()
      else -> error("Unsupported timestamp type: ${value?.javaClass}")
    }

  private fun toBytes(value: Any?): ByteArray? =
    when (value) {
      null -> null
      is ByteArray -> value
      is ByteBuffer -> {
        val copy = ByteArray(value.remaining())
        value.get(copy)
        copy
      }
      else -> error("Unsupported bytea type: ${value.javaClass}")
    }
}
