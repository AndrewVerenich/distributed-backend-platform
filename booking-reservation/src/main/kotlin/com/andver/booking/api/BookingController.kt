package com.andver.booking.api

import com.andver.booking.payment.PaymentService
import com.andver.booking.service.BookingService
import com.andver.booking.service.HoldOutcome
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class BookingController(
  private val booking: BookingService,
  private val payments: PaymentService,
  private val objectMapper: ObjectMapper,
) {

  @GetMapping("/venues/{venueId}/seats")
  fun seats(@PathVariable venueId: String): Mono<VenueSeatsResponse> =
    booking.listSeats(venueId)
      .map { it.toView() }
      .collectList()
      .map { VenueSeatsResponse(venueId = venueId, seats = it) }

  @PostMapping("/holds", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun hold(
    @RequestBody body: HoldRequest,
    @RequestHeader(value = IDEMPOTENCY_KEY, required = false) key: String?,
    @RequestHeader(value = IDEMPOTENCY_OWNER, required = false) owner: String?,
  ): Mono<ResponseEntity<HoldResponse>> {
    val bytes = objectMapper.writeValueAsBytes(body)
    return booking.hold(body.seatId, body.userId, key, owner, bytes)
      .map { outcome ->
        when (outcome) {
          is HoldOutcome.Ok -> ResponseEntity.ok(outcome.response)
          HoldOutcome.Conflict -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
      }
  }

  @PostMapping("/naive/holds", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun naiveHold(@RequestBody body: HoldRequest): Mono<ResponseEntity<HoldResponse>> =
    booking.naiveHold(body.seatId, body.userId)
      .map { outcome ->
        when (outcome) {
          is HoldOutcome.Ok -> ResponseEntity.ok(outcome.response)
          HoldOutcome.Conflict -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
      }

  @PostMapping("/reservations/{id}/confirm")
  fun confirm(
    @PathVariable id: String,
    @RequestHeader(value = IDEMPOTENCY_KEY, required = false) key: String?,
    @RequestHeader(value = IDEMPOTENCY_OWNER, required = false) owner: String?,
  ): Mono<ResponseEntity<ConfirmResponse>> {
    val bytes = """{"reservationId":"$id"}""".toByteArray()
    return booking.confirm(id, key, owner, bytes)
      .map { response ->
        if (response.compensated) {
          ResponseEntity.status(HttpStatus.CONFLICT).body(response)
        } else {
          ResponseEntity.ok(response)
        }
      }
  }

  @PostMapping("/payments", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun pay(@RequestBody body: PaymentRequest): Mono<PaymentResponse> =
    payments.pay(body.reservationId, body.delayMs)

  @PostMapping("/admin/venues/{venueId}/reset")
  fun reset(@PathVariable venueId: String): Mono<ResetResponse> =
    booking.resetVenue(venueId).map { ResetResponse(venueId = venueId, seatsReset = it) }

  companion object {
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
    const val IDEMPOTENCY_OWNER = "Idempotency-Key-Owner"
  }
}
