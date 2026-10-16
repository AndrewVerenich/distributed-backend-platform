package com.andver.booking.service

import com.andver.booking.api.ConfirmResponse
import com.andver.booking.api.HoldResponse
import com.andver.booking.api.toHoldResponse
import com.andver.booking.config.BookingProperties
import com.andver.booking.idempotency.IdempotencyClaim
import com.andver.booking.idempotency.IdempotencyStore
import com.andver.booking.metrics.BookingMetrics
import com.andver.booking.repository.CompensationOutboxRepository
import com.andver.booking.repository.ReservationRepository
import com.andver.booking.repository.SeatRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface HoldOutcome {
  data class Ok(val response: HoldResponse) : HoldOutcome
  data object Conflict : HoldOutcome
}

@Service
class BookingService(
  private val seats: SeatRepository,
  private val reservations: ReservationRepository,
  private val outbox: CompensationOutboxRepository,
  private val idempotency: IdempotencyStore,
  private val properties: BookingProperties,
  private val metrics: BookingMetrics,
  private val tx: TransactionalOperator,
  private val objectMapper: ObjectMapper,
) {

  fun hold(
    seatId: String,
    userId: String,
    idempotencyKey: String?,
    idempotencyOwner: String?,
    bodyBytes: ByteArray,
  ): Mono<HoldOutcome> {
    if (idempotencyKey.isNullOrBlank() || idempotencyOwner.isNullOrBlank()) {
      return Mono.error(
        ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key and Idempotency-Key-Owner required"),
      )
    }
    val owner = idempotencyOwner
    val key = idempotencyKey
    val bodyHash = idempotency.hashBody(bodyBytes)
    return tx.transactional(
      idempotency.claim(owner, key, bodyHash).flatMap { claim ->
        when (claim) {
          is IdempotencyClaim.Proceed ->
            tryHoldInternal(seatId, userId, naive = false)
              .flatMap { response ->
                completeIdempotency(owner, key, claim.requestId, HttpStatus.OK, response)
                  .thenReturn(HoldOutcome.Ok(response) as HoldOutcome)
              }
              .switchIfEmpty(
                Mono.defer {
                  val body = mapOf("error" to "seat unavailable", "seatId" to seatId)
                  completeIdempotency(owner, key, claim.requestId, HttpStatus.CONFLICT, body)
                    .thenReturn(HoldOutcome.Conflict)
                },
              )

          is IdempotencyClaim.Replay -> {
            metrics.idempotencyReplayed()
            if (claim.record.responseStatus == HttpStatus.CONFLICT.value()) {
              Mono.just(HoldOutcome.Conflict)
            } else {
              decodeReplay(claim.record.responseBody, HoldResponse::class.java)
                .map { HoldOutcome.Ok(it) }
            }
          }

          is IdempotencyClaim.InProgress -> {
            metrics.idempotencyConflict()
            Mono.error(
              ResponseStatusException(
                HttpStatus.CONFLICT,
                "idempotency key in progress; retryAfter=${claim.retryAfter.seconds.coerceAtLeast(1)}s",
              ),
            )
          }

          IdempotencyClaim.BodyConflict -> {
            metrics.idempotencyConflict()
            Mono.error(
              ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency key reused with different body"),
            )
          }
        }
      },
    )
  }

  fun naiveHold(seatId: String, userId: String): Mono<HoldOutcome> =
    tx.transactional(
      tryHoldInternal(seatId, userId, naive = true)
        .map { HoldOutcome.Ok(it) as HoldOutcome }
        .switchIfEmpty(Mono.just(HoldOutcome.Conflict)),
    )

  fun confirm(
    reservationId: String,
    idempotencyKey: String?,
    idempotencyOwner: String?,
    bodyBytes: ByteArray,
  ): Mono<ConfirmResponse> {
    if (idempotencyKey.isNullOrBlank() || idempotencyOwner.isNullOrBlank()) {
      return Mono.error(
        ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key and Idempotency-Key-Owner required"),
      )
    }
    val owner = idempotencyOwner
    val key = idempotencyKey
    val bodyHash = idempotency.hashBody(bodyBytes)
    return tx.transactional(
      idempotency.claim(owner, key, bodyHash).flatMap { claim ->
        when (claim) {
          is IdempotencyClaim.Proceed ->
            tryConfirmInternal(reservationId).flatMap { response ->
              val status = if (response.compensated) HttpStatus.CONFLICT else HttpStatus.OK
              completeIdempotency(owner, key, claim.requestId, status, response).thenReturn(response)
            }

          is IdempotencyClaim.Replay -> {
            metrics.idempotencyReplayed()
            decodeReplay(claim.record.responseBody, ConfirmResponse::class.java)
          }

          is IdempotencyClaim.InProgress -> {
            metrics.idempotencyConflict()
            Mono.error(
              ResponseStatusException(
                HttpStatus.CONFLICT,
                "idempotency key in progress; retryAfter=${claim.retryAfter.seconds.coerceAtLeast(1)}s",
              ),
            )
          }

          IdempotencyClaim.BodyConflict -> {
            metrics.idempotencyConflict()
            Mono.error(
              ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency key reused with different body"),
            )
          }
        }
      },
    )
  }

  fun listSeats(venueId: String) = seats.findByVenue(venueId)

  fun resetVenue(venueId: String): Mono<Long> =
    tx.transactional(
      reservations.deleteByVenue(venueId)
        .then(seats.resetVenue(venueId)),
    )

  private fun tryHoldInternal(seatId: String, userId: String, naive: Boolean): Mono<HoldResponse> {
    val reservationId = UUID.randomUUID().toString()
    val ttl = properties.holdTtl
    val holdMono = if (naive) {
      seats.naiveHold(seatId, userId, reservationId, ttl)
    } else {
      seats.tryHold(seatId, userId, reservationId, ttl)
    }
    return holdMono
      .flatMap { seat ->
        reservations.insert(reservationId, seatId, userId, seat.holdUntil)
          .flatMap { reservation ->
            if (naive) {
              metrics.naiveHoldsOk()
              reservations.countActiveForSeat(seatId).map { count ->
                if (count > 1) {
                  metrics.oversell()
                }
                reservation.toHoldResponse(mode = "naive")
              }
            } else {
              metrics.holdsOk()
              Mono.just(reservation.toHoldResponse(mode = "correct"))
            }
          }
      }
      .switchIfEmpty(
        Mono.defer {
          metrics.holdConflicts()
          Mono.empty()
        },
      )
  }

  private fun tryConfirmInternal(reservationId: String): Mono<ConfirmResponse> =
    reservations.findById(reservationId)
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "reservation not found")))
      .flatMap { reservation ->
        seats.tryConfirm(reservation.seatId, reservationId)
          .flatMap { seat ->
            reservations.markConfirmed(reservationId).map {
              metrics.confirmsOk()
              ConfirmResponse(
                reservationId = reservationId,
                seatId = seat.id,
                status = "confirmed",
                compensated = false,
              )
            }
          }
          .switchIfEmpty(
            Mono.defer {
              val payload =
                """{"reservationId":"$reservationId","seatId":"${reservation.seatId}","reason":"confirm_after_ttl_or_lost_hold"}"""
              outbox.insert(reservationId, payload)
                .then(reservations.markCompensated(reservationId))
                .map {
                  metrics.confirmConflicts()
                  metrics.compensations()
                  ConfirmResponse(
                    reservationId = reservationId,
                    seatId = reservation.seatId,
                    status = "compensated",
                    compensated = true,
                  )
                }
            },
          )
      }

  private fun completeIdempotency(
    owner: String,
    key: String,
    requestId: String,
    status: HttpStatus,
    body: Any,
  ): Mono<Void> {
    val json = objectMapper.writeValueAsBytes(body)
    return idempotency.complete(
      owner = owner,
      key = key,
      requestId = requestId,
      responseStatus = status.value(),
      contentType = "application/json",
      body = json,
    ).then()
  }

  private fun <T : Any> decodeReplay(body: ByteArray?, type: Class<T>): Mono<T> {
    if (body == null) {
      return Mono.error(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "empty idempotent replay"))
    }
    return try {
      Mono.just(objectMapper.readValue(String(body, StandardCharsets.UTF_8), type))
    } catch (e: Exception) {
      Mono.error(ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to decode replay: ${e.message}"))
    }
  }
}
