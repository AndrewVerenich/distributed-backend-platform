package com.andver.saga.car

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

@Component
class CarRentalHandler(
  private val objectMapper: ObjectMapper
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(CarRentalHandler::class.java)
  override val commandType = "rent-car"

  override fun handle(command: SagaCommand): Mono<SagaReply> {
    return Mono.defer {
      val payload = objectMapper.readTree(command.payload)
      val carId = payload.get("carId").asText()
      val driverId = payload.get("driverId").asLong()

      // ~20% failure rate
      if (Math.random() < 0.20) {
        log.warn("Car rental FAILED (simulated) for car={}", carId)
        return@defer Mono.just(SagaReply(
          sagaId = command.sagaId,
          stepName = command.stepName,
          status = ReplyStatus.FAILURE,
          errorMessage = "Car $carId temporarily unavailable"
        ))
      }

      val delayMs = (200L..600L).random()

      Mono.delay(Duration.ofMillis(delayMs))
        .then(
          Mono.fromSupplier {
            val rentalId = UUID.randomUUID().toString()
            log.info("Car rented (RETRYABLE): rentalId={} car={} driver={}", rentalId, carId, driverId)

            SagaReply(
              sagaId = command.sagaId,
              stepName = command.stepName,
              status = ReplyStatus.SUCCESS,
              payload = objectMapper.writeValueAsString(mapOf("rentalId" to rentalId))
            )
          }
        )
    }
  }
}
