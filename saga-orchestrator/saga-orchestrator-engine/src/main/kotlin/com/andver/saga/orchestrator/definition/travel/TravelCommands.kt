package com.andver.saga.orchestrator.definition.travel

data class BookFlightCommand(val flightId: String, val passengerId: Long)
data class CancelFlightCommand(val flightBookingId: String)

data class BookHotelCommand(val hotelId: String, val guestId: Long)

data class RentCarCommand(val carId: String, val driverId: Long)
