package com.andver.choreography.car

import com.andver.choreography.model.ChoreographyEventTypes
import com.andver.choreography.model.TravelBookingPayload
import com.andver.inbox.handler.AbstractInboxEventHandler
import com.andver.outbox.publisher.OutboxPublisher
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.r2dbc.repository.Query
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@SpringBootApplication
@EnableR2dbcRepositories
class TravelCarServiceApp

fun main(args: Array<String>) {
  SpringApplication.run(TravelCarServiceApp::class.java, *args)
}

/**
 * Post-pivot retryable step: failures do NOT compensate hotel/flight.
 * Transient failures throw so Kafka redelivers; inbox stays non-PROCESSED until success.
 * After [maxRetries] transient attempts we publish car-rental-failed (signal only).
 */
@Service
class CarRentalService(
  private val carRentalRepository: CarRentalRepository,
  private val outboxPublisher: OutboxPublisher,
  private val meterRegistry: MeterRegistry,
  @Value("\${choreography.failure-rate:0.0}") private val failureRate: Double,
  @Value("\${choreography.max-retries:3}") private val maxRetries: Int,
) {
  private val log = LoggerFactory.getLogger(CarRentalService::class.java)
  private val attemptCounters = ConcurrentHashMap<String, AtomicInteger>()

  @Transactional
  fun rentCar(payload: TravelBookingPayload): Mono<Void> {
    val attempts = attemptCounters.computeIfAbsent(payload.bookingId) { AtomicInteger(0) }
    val attempt = attempts.incrementAndGet()

    if (Math.random() < failureRate) {
      if (attempt <= maxRetries) {
        meterRegistry.counter("choreography.step.retries", "step", "rent-car").increment()
        log.warn(
          "Car rental FAILED (simulated, post-pivot retryable) bookingId={} attempt={}/{}",
          payload.bookingId,
          attempt,
          maxRetries,
        )
        return Mono.error(
          IllegalStateException("Car ${payload.carId} temporarily unavailable (attempt $attempt)")
        )
      }

      log.error(
        "Car rental permanently failed for bookingId={} after {} attempts (no hotel/flight compensation)",
        payload.bookingId,
        attempt,
      )
      attemptCounters.remove(payload.bookingId)
      val failed = payload.copy(errorMessage = "Car ${payload.carId} unavailable after $maxRetries retries")
      return outboxPublisher.publish(
        partitioningKey = payload.bookingId,
        eventType = ChoreographyEventTypes.CAR_RENTAL_FAILED,
        payload = failed,
      ).then()
    }

    val carRentalId = UUID.randomUUID()
    val entity = CarRentalEntity(
      carRentalId = carRentalId,
      bookingId = UUID.fromString(payload.bookingId),
      carId = payload.carId,
      driverId = payload.passengerId,
      status = "RENTED",
      retryCount = attempt - 1,
      newEntity = true,
    )
    val enriched = payload.copy(carRentalId = carRentalId.toString())
    return carRentalRepository.save(entity)
      .flatMap {
        outboxPublisher.publish(
          partitioningKey = payload.bookingId,
          eventType = ChoreographyEventTypes.CAR_RENTED,
          payload = enriched,
        )
      }
      .doOnSuccess {
        attemptCounters.remove(payload.bookingId)
        log.info(
          "Car rented (RETRYABLE): carRentalId={} bookingId={} retries={}",
          carRentalId,
          payload.bookingId,
          attempt - 1,
        )
      }
      .then()
  }
}

@Table("car_rentals")
data class CarRentalEntity(
  @Id
  @Column("car_rental_id")
  val carRentalId: UUID,
  @Column("booking_id")
  val bookingId: UUID,
  @Column("car_id")
  val carId: String,
  @Column("driver_id")
  val driverId: Long,
  val status: String,
  @Column("retry_count")
  val retryCount: Int = 0,
  @Column("created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
  @Column("updated_at")
  val updatedAt: LocalDateTime = LocalDateTime.now(),
  @Transient
  private val newEntity: Boolean = false,
) : Persistable<UUID> {
  override fun getId(): UUID = carRentalId
  @Transient
  override fun isNew(): Boolean = newEntity
}

interface CarRentalRepository : ReactiveCrudRepository<CarRentalEntity, UUID> {
  @Query("SELECT * FROM car_rentals WHERE booking_id = :bookingId")
  fun findByBookingId(bookingId: UUID): Mono<CarRentalEntity>
}

@Component
class HotelBookedHandler(
  private val carRentalService: CarRentalService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.HOTEL_BOOKED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    return carRentalService.rentCar(payload)
  }
}
