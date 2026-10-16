package com.andver.booking.model

import java.time.Instant

enum class SeatStatus {
  FREE, HELD, SOLD;

  fun dbValue(): String = name.lowercase()

  companion object {
    fun fromDb(value: String): SeatStatus =
      entries.first { it.dbValue() == value.lowercase() }
  }
}

enum class ReservationStatus {
  HELD, CONFIRMED, EXPIRED, COMPENSATED;

  fun dbValue(): String = name.lowercase()

  companion object {
    fun fromDb(value: String): ReservationStatus =
      entries.first { it.dbValue() == value.lowercase() }
  }
}

data class Seat(
  val id: String,
  val venueId: String,
  val label: String,
  val rowNum: Int,
  val colNum: Int,
  val status: SeatStatus,
  val holderId: String? = null,
  val reservationId: String? = null,
  val holdUntil: Instant? = null,
)

data class Reservation(
  val id: String,
  val seatId: String,
  val userId: String,
  val status: ReservationStatus,
  val holdUntil: Instant? = null,
  val createdAt: Instant,
  val confirmedAt: Instant? = null,
)
