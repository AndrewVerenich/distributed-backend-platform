package com.andver.saga.hotel

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
class HotelBookingHandler(
  private val objectMapper: ObjectMapper
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(HotelBookingHandler::class.java)
  override val commandType = "book-hotel"

  override fun handle(command: SagaCommand): Mono<SagaReply> {
    return Mono.defer {
      val payload = objectMapper.readTree(command.payload)
      val hotelId = payload.get("hotelId").asText()
      val guestId = payload.get("guestId").asLong()

      val delayMs = (300L..1000L).random()

      Mono.delay(Duration.ofMillis(delayMs))
        .then(
          Mono.fromSupplier {
            val bookingId = UUID.randomUUID().toString()
            log.info("Hotel booked (PIVOT): bookingId={} hotel={} guest={}", bookingId, hotelId, guestId)

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
}
