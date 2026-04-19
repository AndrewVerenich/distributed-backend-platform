package com.andver.saga.orchestrator.definition.travel

data class TravelBookingData(
  val passengerId: Long,
  val flightId: String,
  val hotelId: String,
  val carId: String,
  val flightBookingId: String? = null,
  val hotelBookingId: String? = null,
  val carRentalId: String? = null
)
