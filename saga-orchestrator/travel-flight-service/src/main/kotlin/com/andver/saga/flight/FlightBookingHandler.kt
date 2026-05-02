package com.andver.saga.flight

import com.andver.saga.model.ReplyStatus
import com.andver.saga.model.SagaCommand
import com.andver.saga.model.SagaReply
import com.andver.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class FlightBookingHandler(
  private val objectMapper: ObjectMapper
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(FlightBookingHandler::class.java)
  override val commandType = "book-flight"

  private val bookings = ConcurrentHashMap<String, FlightBooking>()

  override fun handle(command: SagaCommand): Mono<SagaReply> {
    return Mono.defer {
      val payload = objectMapper.readTree(command.payload)
      val flightId = payload.get("flightId").asText()
      val passengerId = payload.get("passengerId").asLong()

      // ~10% failure rate for demo
      if (Math.random() < 0.10) {
        log.warn("Flight booking FAILED (simulated) for flight={}", flightId)
        return@defer Mono.just(SagaReply(
          sagaId = command.sagaId,
          stepName = command.stepName,
          status = ReplyStatus.FAILURE,
          errorMessage = "Flight $flightId is fully booked"
        ))
      }

      val delayMs = (200L..800L).random()

      Mono.delay(Duration.ofMillis(delayMs))
        .then(
          Mono.fromSupplier {
            val bookingId = UUID.randomUUID().toString()
            bookings[bookingId] = FlightBooking(bookingId, flightId, passengerId)
            log.info("Flight booked: bookingId={} flight={} passenger={}", bookingId, flightId, passengerId)

            SagaReply(
              sagaId = command.sagaId,
              stepName = command.stepName,
              status = ReplyStatus.SUCCESS,
              payload = objectMapper.writeValueAsString(mapOf("bookingId" to bookingId))
            )
          }
        )
    }
  }

  override fun compensate(command: SagaCommand): Mono<SagaReply> {
    return Mono.defer {
      val payload = objectMapper.readTree(command.payload)
      val flightBookingId = payload.get("flightBookingId").asText()

      val delayMs = (100L..300L).random()

      Mono.delay(Duration.ofMillis(delayMs))
        .then(
          Mono.fromSupplier {
            bookings.remove(flightBookingId)
            log.info("Flight booking CANCELLED: bookingId={}", flightBookingId)

            SagaReply(
              sagaId = command.sagaId,
              stepName = command.stepName,
              status = ReplyStatus.SUCCESS,
              isCompensation = true,
              payload = objectMapper.writeValueAsString(mapOf("cancelledBookingId" to flightBookingId))
            )
          }
        )
    }
  }
}

data class FlightBooking(val bookingId: String, val flightId: String, val passengerId: Long)
