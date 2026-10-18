package com.andver.choreography.model

object ChoreographyEventTypes {
  const val BOOKING_REQUESTED = "booking-requested"
  const val FLIGHT_BOOKED = "flight-booked"
  const val FLIGHT_BOOKING_FAILED = "flight-booking-failed"
  const val FLIGHT_BOOKING_CANCELLED = "flight-booking-cancelled"
  const val HOTEL_BOOKED = "hotel-booked"
  const val HOTEL_BOOKING_FAILED = "hotel-booking-failed"
  const val CAR_RENTED = "car-rented"
  const val CAR_RENTAL_FAILED = "car-rental-failed"
  const val BOOKING_COMPLETED = "booking-completed"
  const val BOOKING_COMPENSATED = "booking-compensated"

  val ALL = listOf(
    BOOKING_REQUESTED,
    FLIGHT_BOOKED,
    FLIGHT_BOOKING_FAILED,
    FLIGHT_BOOKING_CANCELLED,
    HOTEL_BOOKED,
    HOTEL_BOOKING_FAILED,
    CAR_RENTED,
    CAR_RENTAL_FAILED,
    BOOKING_COMPLETED,
    BOOKING_COMPENSATED,
  )

  fun topic(eventType: String) = "domain.$eventType"
}

/**
 * Shared correlation payload carried through the choreography chain.
 * Each step enriches optional IDs; failures set [errorMessage].
 */
data class TravelBookingPayload(
  val bookingId: String,
  val passengerId: Long,
  val flightId: String,
  val hotelId: String,
  val carId: String,
  val flightBookingId: String? = null,
  val hotelBookingId: String? = null,
  val carRentalId: String? = null,
  val errorMessage: String? = null,
)

enum class BookingStatus {
  PENDING,
  COMPLETED,
  FAILED,
  COMPENSATED,
}
