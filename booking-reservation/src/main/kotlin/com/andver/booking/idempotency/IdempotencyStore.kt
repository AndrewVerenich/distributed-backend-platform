package com.andver.booking.idempotency

import com.andver.booking.config.BookingProperties
import io.r2dbc.spi.Readable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.HexFormat
import java.util.UUID

data class IdempotencyRecord(
  val owner: String,
  val key: String,
  val bodyHash: String,
  val status: String,
  val leaseUntil: Instant,
  val requestId: String,
  val responseStatus: Int? = null,
  val responseContentType: String? = null,
  val responseBody: ByteArray? = null,
)

sealed interface IdempotencyClaim {
  data class Proceed(val requestId: String) : IdempotencyClaim
  data class Replay(val record: IdempotencyRecord) : IdempotencyClaim
  data class InProgress(val retryAfter: Duration) : IdempotencyClaim
  data object BodyConflict : IdempotencyClaim
}

@Component
class IdempotencyStore(
  private val databaseClient: DatabaseClient,
  private val properties: BookingProperties,
) {

  fun hashBody(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return HexFormat.of().formatHex(digest)
  }

  fun claim(owner: String, key: String, bodyHash: String): Mono<IdempotencyClaim> {
    val requestId = UUID.randomUUID().toString()
    val leaseUntil = Instant.now().plus(properties.idempotency.lease)
    return tryInsert(owner, key, bodyHash, requestId, leaseUntil)
      .map<IdempotencyClaim> { IdempotencyClaim.Proceed(requestId) }
      .switchIfEmpty(Mono.defer { resolveExisting(owner, key, bodyHash) })
  }

  fun complete(
    owner: String,
    key: String,
    requestId: String,
    responseStatus: Int,
    contentType: String,
    body: ByteArray,
  ): Mono<Boolean> {
    val expiresAt = Instant.now().plus(properties.idempotency.retention)
    return databaseClient.sql(
      """
      UPDATE idempotency_records
         SET status = 'completed',
             response_status = :responseStatus,
             response_content_type = :contentType,
             response_body = :body,
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
      .bind("contentType", contentType)
      .bind("body", body)
      .bind("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
      .fetch()
      .rowsUpdated()
      .map { it > 0 }
  }

  private fun tryInsert(
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
                response_status, response_content_type, response_body
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .bind("bodyHash", bodyHash)
      .bind("requestId", requestId)
      .bind("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
      .map { row, _ -> mapRecord(row) }
      .one()

  private fun resolveExisting(owner: String, key: String, bodyHash: String): Mono<IdempotencyClaim> =
    find(owner, key).flatMap { record ->
      when {
        record.status == "completed" -> {
          if (record.bodyHash != bodyHash) {
            Mono.just(IdempotencyClaim.BodyConflict)
          } else {
            Mono.just(IdempotencyClaim.Replay(record))
          }
        }
        record.bodyHash != bodyHash -> Mono.just(IdempotencyClaim.BodyConflict)
        record.leaseUntil.isAfter(Instant.now()) -> {
          val remaining = Duration.between(Instant.now(), record.leaseUntil)
          Mono.just(IdempotencyClaim.InProgress(if (remaining.isNegative) Duration.ofSeconds(1) else remaining))
        }
        else -> takeOver(owner, key, bodyHash)
      }
    }

  private fun takeOver(owner: String, key: String, bodyHash: String): Mono<IdempotencyClaim> {
    val requestId = UUID.randomUUID().toString()
    val leaseUntil = Instant.now().plus(properties.idempotency.lease)
    return databaseClient.sql(
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
                response_status, response_content_type, response_body
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .bind("bodyHash", bodyHash)
      .bind("requestId", requestId)
      .bind("leaseUntil", leaseUntil.atOffset(ZoneOffset.UTC))
      .map { row, _ -> mapRecord(row) }
      .one()
      .map<IdempotencyClaim> { IdempotencyClaim.Proceed(requestId) }
      .switchIfEmpty(
        find(owner, key).map { record ->
          when {
            record.status == "completed" && record.bodyHash == bodyHash -> IdempotencyClaim.Replay(record)
            record.bodyHash != bodyHash -> IdempotencyClaim.BodyConflict
            else -> IdempotencyClaim.InProgress(Duration.ofSeconds(1))
          }
        },
      )
  }

  private fun find(owner: String, key: String): Mono<IdempotencyRecord> =
    databaseClient.sql(
      """
      SELECT owner_id, idempotency_key, body_hash, status, lease_until, request_id,
             response_status, response_content_type, response_body
        FROM idempotency_records
       WHERE owner_id = :owner AND idempotency_key = :key
      """.trimIndent(),
    )
      .bind("owner", owner)
      .bind("key", key)
      .map { row, _ -> mapRecord(row) }
      .one()

  private fun mapRecord(row: Readable): IdempotencyRecord =
    IdempotencyRecord(
      owner = row.get("owner_id", String::class.java)!!,
      key = row.get("idempotency_key", String::class.java)!!,
      bodyHash = row.get("body_hash", String::class.java)!!,
      status = row.get("status", String::class.java)!!,
      leaseUntil = toInstant(row.get("lease_until")!!),
      requestId = row.get("request_id", String::class.java)!!,
      responseStatus = row.get("response_status", Integer::class.java)?.toInt(),
      responseContentType = row.get("response_content_type", String::class.java),
      responseBody = toBytes(row.get("response_body")),
    )

  private fun toInstant(value: Any): Instant =
    when (value) {
      is Instant -> value
      is OffsetDateTime -> value.toInstant()
      else -> error("Unsupported timestamp: ${value.javaClass}")
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
      else -> error("Unsupported bytea: ${value.javaClass}")
    }
}
