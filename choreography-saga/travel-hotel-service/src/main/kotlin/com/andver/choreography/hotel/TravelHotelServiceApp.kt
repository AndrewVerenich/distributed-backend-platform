package com.andver.choreography.hotel

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
class TravelHotelServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(TravelHotelServiceApp::class.java, *args)
}

/**
 * Hotel is the pivot step: on failure it publishes hotel-booking-failed (triggers flight compensation).
 * On success it publishes hotel-booked; there is no compensate path after this point.
 */
@Service
class HotelBookingService(
  private val hotelBookingRepository: HotelBookingRepository,
  private val outboxPublisher: OutboxPublisher,
  @Value("\${choreography.failure-rate:0.0}") private val failureRate: Double,
) {
  private val log = LoggerFactory.getLogger(HotelBookingService::class.java)

  @Transactional
  fun bookHotel(payload: TravelBookingPayload): Mono<Void> {
    if (Math.random() < failureRate) {
      log.warn("Hotel booking FAILED (simulated, pre-pivot) for bookingId={}", payload.bookingId)
      val failed = payload.copy(errorMessage = "Hotel ${payload.hotelId} has no availability")
      return outboxPublisher.publish(
        partitioningKey = payload.bookingId,
        eventType = ChoreographyEventTypes.HOTEL_BOOKING_FAILED,
        payload = failed,
      ).then()
    }

    val hotelBookingId = UUID.randomUUID()
    val entity = HotelBookingEntity(
      hotelBookingId = hotelBookingId,
      bookingId = UUID.fromString(payload.bookingId),
      hotelId = payload.hotelId,
      guestId = payload.passengerId,
      status = "BOOKED",
      newEntity = true,
    )
    val enriched = payload.copy(hotelBookingId = hotelBookingId.toString())
    return hotelBookingRepository.save(entity)
      .flatMap {
        outboxPublisher.publish(
          partitioningKey = payload.bookingId,
          eventType = ChoreographyEventTypes.HOTEL_BOOKED,
          payload = enriched,
        )
      }
      .doOnSuccess {
        log.info(
          "Hotel booked (PIVOT): hotelBookingId={} bookingId={}",
          hotelBookingId,
          payload.bookingId,
        )
      }
      .then()
  }
}

@Table("hotel_bookings")
data class HotelBookingEntity(
  @Id
  @Column("hotel_booking_id")
  val hotelBookingId: UUID,
  @Column("booking_id")
  val bookingId: UUID,
  @Column("hotel_id")
  val hotelId: String,
  @Column("guest_id")
  val guestId: Long,
  val status: String,
  @Column("created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
  @Column("updated_at")
  val updatedAt: LocalDateTime = LocalDateTime.now(),
  @Transient
  private val newEntity: Boolean = false,
) : Persistable<UUID> {
  override fun getId(): UUID = hotelBookingId
  @Transient
  override fun isNew(): Boolean = newEntity
}

interface HotelBookingRepository : ReactiveCrudRepository<HotelBookingEntity, UUID>

@Component
class FlightBookedHandler(
  private val hotelBookingService: HotelBookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.FLIGHT_BOOKED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    return hotelBookingService.bookHotel(payload)
  }
}
