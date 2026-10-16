package com.andver.idempotency.server.service

import com.andver.idempotency.api.IdempotencyStatus
import com.andver.idempotency.server.IdempotencyContext
import com.andver.idempotency.server.IdempotencyServerProperties
import com.andver.idempotency.server.metrics.IdempotencyServerMetrics
import com.andver.idempotency.server.repository.IdempotencyRecord
import com.andver.idempotency.server.repository.IdempotencyRepository
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class IdempotencyClaimService(
  private val repository: IdempotencyRepository,
  private val properties: IdempotencyServerProperties,
  private val metrics: IdempotencyServerMetrics,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun claim(owner: String, key: String, bodyHash: String): Mono<ClaimResult> {
    val requestId = UUID.randomUUID().toString()
    val leaseUntil = clock.instant().plus(properties.lease)
    return repository.tryInsertStarted(owner, key, bodyHash, requestId, leaseUntil)
      .map<ClaimResult> {
        metrics.started()
        ClaimResult.Proceed(context(owner, key, requestId, bodyHash))
      }
      .switchIfEmpty(Mono.defer { resolveExisting(owner, key, bodyHash) })
  }

  fun complete(
    context: IdempotencyContext,
    responseStatus: Int,
    responseContentType: String?,
    responseBody: ByteArray?,
  ): Mono<Void> {
    val body = truncate(responseBody)
    val expiresAt = clock.instant().plus(properties.retention)
    return repository.complete(
      owner = context.owner,
      key = context.key,
      requestId = context.requestId,
      responseStatus = responseStatus,
      responseContentType = responseContentType,
      responseBody = body,
      expiresAt = expiresAt,
    ).flatMap { updated ->
      if (updated) {
        metrics.completed()
        Mono.empty()
      } else {
        Mono.error(
          IllegalStateException(
            "Idempotency lease lost for owner=${context.owner} key=${context.key} requestId=${context.requestId}",
          ),
        )
      }
    }
  }

  private fun resolveExisting(owner: String, key: String, bodyHash: String): Mono<ClaimResult> =
    repository.find(owner, key)
      .flatMap { record ->
        when {
          record.status == IdempotencyStatus.COMPLETED -> handleCompleted(record, bodyHash)
          record.bodyHash != bodyHash -> {
            metrics.conflictBody()
            Mono.just(ClaimResult.BodyConflict)
          }
          record.leaseUntil.isAfter(clock.instant()) -> {
            metrics.conflictInProgress()
            Mono.just(ClaimResult.InProgress(retryAfter(record)))
          }
          else -> takeOver(owner, key, bodyHash)
        }
      }
      .switchIfEmpty(
        Mono.error(IllegalStateException("Idempotency row vanished for owner=$owner key=$key")),
      )

  private fun handleCompleted(record: IdempotencyRecord, bodyHash: String): Mono<ClaimResult> =
    if (record.bodyHash != bodyHash) {
      metrics.conflictBody()
      Mono.just(ClaimResult.BodyConflict)
    } else {
      metrics.replayed()
      Mono.just(ClaimResult.Replay(record))
    }

  private fun takeOver(owner: String, key: String, bodyHash: String): Mono<ClaimResult> {
    val requestId = UUID.randomUUID().toString()
    val leaseUntil = clock.instant().plus(properties.lease)
    return repository.takeOverExpiredLease(owner, key, bodyHash, requestId, leaseUntil)
      .map<ClaimResult> {
        metrics.leaseTakenOver()
        ClaimResult.Proceed(context(owner, key, requestId, bodyHash))
      }
      .switchIfEmpty(
        repository.find(owner, key).map { record ->
          when {
            record.status == IdempotencyStatus.COMPLETED && record.bodyHash == bodyHash -> {
              metrics.replayed()
              ClaimResult.Replay(record)
            }
            record.bodyHash != bodyHash -> {
              metrics.conflictBody()
              ClaimResult.BodyConflict
            }
            else -> {
              metrics.conflictInProgress()
              ClaimResult.InProgress(retryAfter(record))
            }
          }
        },
      )
  }

  private fun retryAfter(record: IdempotencyRecord): Duration {
    val remaining = Duration.between(clock.instant(), record.leaseUntil)
    return if (remaining.isNegative || remaining.isZero) Duration.ofSeconds(1) else remaining
  }

  private fun truncate(body: ByteArray?): ByteArray? {
    if (body == null) return null
    if (body.size <= properties.maxBodyBytes) return body
    return body.copyOf(properties.maxBodyBytes)
  }

  private fun context(owner: String, key: String, requestId: String, bodyHash: String) =
    IdempotencyContext(owner = owner, key = key, requestId = requestId, bodyHash = bodyHash)
}
