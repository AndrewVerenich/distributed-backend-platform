package com.andver.saga.orchestrator.definition.travel

import com.andver.saga.model.StepType
import com.andver.saga.orchestrator.dsl.SagaDefinition
import com.andver.saga.orchestrator.dsl.saga
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class TravelBookingSagaDefinition {

  @Bean
  fun travelBookingSaga(): SagaDefinition<TravelBookingData> =
    saga<TravelBookingData>("travel-booking") {

      step("book-flight") {
        type = StepType.COMPENSABLE
        participant = "flight-service"
        command { data -> BookFlightCommand(data.flightId, data.passengerId) }
        onReply { data, reply ->
          data.copy(flightBookingId = reply.get("bookingId")?.asText())
        }
        compensation { data -> CancelFlightCommand(data.flightBookingId!!) }
        timeout = Duration.ofSeconds(30)
      }

      step("book-hotel") {
        type = StepType.PIVOT
        participant = "hotel-service"
        command { data -> BookHotelCommand(data.hotelId, data.passengerId) }
        onReply { data, reply ->
          data.copy(hotelBookingId = reply.get("bookingId")?.asText())
        }
        timeout = Duration.ofSeconds(30)
      }

      step("rent-car") {
        type = StepType.RETRYABLE
        participant = "car-service"
        command { data -> RentCarCommand(data.carId, data.passengerId) }
        onReply { data, reply ->
          data.copy(carRentalId = reply.get("rentalId")?.asText())
        }
        maxRetries = 3
        retryBackoff = Duration.ofSeconds(5)
        timeout = Duration.ofSeconds(30)
      }
    }
}
