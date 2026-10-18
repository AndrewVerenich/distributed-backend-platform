package com.andver.choreography.flight

import com.andver.choreography.model.ChoreographyEventTypes
import com.andver.choreography.model.TravelBookingPayload
import com.andver.inbox.handler.AbstractInboxEventHandler
import com.andver.outbox.publisher.OutboxPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@SpringBootApplication
@EnableR2dbcRepositories
class TravelFlightServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(TravelFlightServiceApp::class.java, *args)
}

@Service
class FlightBookingService(
  private val flightBookingRepository: FlightBookingRepository,
  private val outboxPublisher: OutboxPublisher,
  @Value("\${choreography.failure-rate:0.0}") private val failureRate: Double,
) {
  private val log = LoggerFactory.getLogger(FlightBookingService::class.java)

  @Transactional
  fun bookFlight(payload: TravelBookingPayload): Mono<Void> {
    if (Math.random() < failureRate) {
      log.warn("Flight booking FAILED (simulated) for bookingId={}", payload.bookingId)
      val failed = payload.copy(errorMessage = "Flight ${payload.flightId} is fully booked")
      return outboxPublisher.publish(
        partitioningKey = payload.bookingId,
        eventType = ChoreographyEventTypes.FLIGHT_BOOKING_FAILED,
        payload = failed,
      ).then()
    }

    val flightBookingId = UUID.randomUUID()
    val entity = FlightBookingEntity(
      flightBookingId = flightBookingId,
      bookingId = UUID.fromString(payload.bookingId),
      flightId = payload.flightId,
      passengerId = payload.passengerId,
      status = "BOOKED",
      newEntity = true,
    )
    val enriched = payload.copy(flightBookingId = flightBookingId.toString())
    return flightBookingRepository.save(entity)
      .flatMap {
        outboxPublisher.publish(
          partitioningKey = payload.bookingId,
          eventType = ChoreographyEventTypes.FLIGHT_BOOKED,
          payload = enriched,
        )
      }
      .doOnSuccess {
        log.info(
          "Flight booked: flightBookingId={} bookingId={} flight={}",
          flightBookingId,
          payload.bookingId,
          payload.flightId,
        )
      }
      .then()
  }

  @Transactional
  fun cancelFlight(payload: TravelBookingPayload): Mono<Void> {
    val bookingId = UUID.fromString(payload.bookingId)
    return flightBookingRepository.findByBookingId(bookingId)
      .flatMap { existing ->
        val cancelled = existing.copy(status = "CANCELLED", updatedAt = LocalDateTime.now())
        flightBookingRepository.save(cancelled)
          .flatMap {
            outboxPublisher.publish(
              partitioningKey = payload.bookingId,
              eventType = ChoreographyEventTypes.FLIGHT_BOOKING_CANCELLED,
              payload = payload.copy(
                flightBookingId = existing.flightBookingId.toString(),
                errorMessage = payload.errorMessage ?: "Compensated after hotel failure",
              ),
            )
          }
          .doOnSuccess {
            log.info("Flight booking CANCELLED: bookingId={}", payload.bookingId)
          }
          .then()
      }
      .switchIfEmpty(
        Mono.defer {
          log.warn("No flight booking to cancel for bookingId={}", payload.bookingId)
          outboxPublisher.publish(
            partitioningKey = payload.bookingId,
            eventType = ChoreographyEventTypes.FLIGHT_BOOKING_CANCELLED,
            payload = payload.copy(errorMessage = payload.errorMessage ?: "Nothing to cancel"),
          ).then()
        }
      )
  }
}

@Table("flight_bookings")
data class FlightBookingEntity(
  @Id
  @Column("flight_booking_id")
  val flightBookingId: UUID,
  @Column("booking_id")
  val bookingId: UUID,
  @Column("flight_id")
  val flightId: String,
  @Column("passenger_id")
  val passengerId: Long,
  val status: String,
  @Column("created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
  @Column("updated_at")
  val updatedAt: LocalDateTime = LocalDateTime.now(),
  @Transient
  private val newEntity: Boolean = false,
) : Persistable<UUID> {
  override fun getId(): UUID = flightBookingId
  @Transient
  override fun isNew(): Boolean = newEntity
}

interface FlightBookingRepository : ReactiveCrudRepository<FlightBookingEntity, UUID> {
  fun findByBookingId(bookingId: UUID): Mono<FlightBookingEntity>
}

@Component
class BookingRequestedHandler(
  private val flightBookingService: FlightBookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.BOOKING_REQUESTED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    return flightBookingService.bookFlight(payload)
  }
}

@Component
class HotelBookingFailedHandler(
  private val flightBookingService: FlightBookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.HOTEL_BOOKING_FAILED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    log.info("Compensating flight for bookingId={} after hotel failure", payload.bookingId)
    return flightBookingService.cancelFlight(payload)
  }
}
