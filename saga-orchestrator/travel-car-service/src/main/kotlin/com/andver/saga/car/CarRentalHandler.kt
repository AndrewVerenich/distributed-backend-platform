package com.andver.saga.car

import com.andver.saga.model.ReplyStatus
import com.andver.saga.model.SagaCommand
import com.andver.saga.model.SagaReply
import com.andver.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class CarRentalHandler(
  private val objectMapper: ObjectMapper
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(CarRentalHandler::class.java)
  override val commandType = "car-service"

  override fun handle(command: SagaCommand): Mono<SagaReply> {
    return Mono.fromCallable {
      val payload = objectMapper.readTree(command.payload)
      val carId = payload.get("carId").asText()
      val driverId = payload.get("driverId").asLong()

      // ~20% failure rate to demonstrate retries
      if (Math.random() < 0.20) {
        log.warn("Car rental FAILED (simulated) for car={}", carId)
        return@fromCallable SagaReply(
          sagaId = command.sagaId,
          stepName = command.stepName,
          status = ReplyStatus.FAILURE,
          errorMessage = "Car $carId temporarily unavailable"
        )
      }

      Thread.sleep((200L..600L).random())

      val rentalId = UUID.randomUUID().toString()
      log.info("Car rented (RETRYABLE): rentalId={} car={} driver={}", rentalId, carId, driverId)

      SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.SUCCESS,
        payload = objectMapper.writeValueAsString(mapOf("rentalId" to rentalId))
      )
    }
  }
}
