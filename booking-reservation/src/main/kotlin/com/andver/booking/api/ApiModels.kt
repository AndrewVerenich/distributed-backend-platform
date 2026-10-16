package com.andver.booking.api

import com.andver.booking.model.Reservation
import com.andver.booking.model.Seat
import java.time.Instant

data class HoldRequest(
  val seatId: String,
  val userId: String,
)

data class HoldResponse(
  val reservationId: String,
  val seatId: String,
  val userId: String,
  val status: String,
  val holdUntil: Instant?,
  val mode: String = "correct",
)

data class ConfirmResponse(
  val reservationId: String,
  val seatId: String,
  val status: String,
  val compensated: Boolean = false,
)

data class SeatView(
  val id: String,
  val label: String,
  val row: Int,
  val col: Int,
  val status: String,
  val holderId: String?,
  val reservationId: String?,
  val holdUntil: Instant?,
)

data class VenueSeatsResponse(
  val venueId: String,
  val seats: List<SeatView>,
)

data class PaymentRequest(
  val reservationId: String,
  val delayMs: Long = 0,
)

data class PaymentResponse(
  val paymentId: String,
  val reservationId: String,
  val status: String,
)

data class ErrorBody(
  val error: String,
  val detail: String? = null,
)

data class ResetResponse(
  val venueId: String,
  val seatsReset: Long,
)

fun Seat.toView() = SeatView(
  id = id,
  label = label,
  row = rowNum,
  col = colNum,
  status = status.dbValue(),
  holderId = holderId,
  reservationId = reservationId,
  holdUntil = holdUntil,
)

fun Reservation.toHoldResponse(mode: String = "correct") = HoldResponse(
  reservationId = id,
  seatId = seatId,
  userId = userId,
  status = status.dbValue(),
  holdUntil = holdUntil,
  mode = mode,
)
