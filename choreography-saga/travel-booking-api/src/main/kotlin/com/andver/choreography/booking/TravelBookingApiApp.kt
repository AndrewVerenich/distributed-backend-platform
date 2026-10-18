package com.andver.choreography.booking

import com.andver.choreography.model.BookingStatus
import com.andver.choreography.model.ChoreographyEventTypes
import com.andver.choreography.model.TravelBookingPayload
import com.andver.inbox.handler.AbstractInboxEventHandler
import com.andver.outbox.publisher.OutboxPublisher
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@SpringBootApplication
@EnableR2dbcRepositories
class TravelBookingApiApp

fun main(args: Array<String>) {
  SpringApplication.run(TravelBookingApiApp::class.java, *args)
}

data class CreateBookingRequest(
  val passengerId: Long,
  val flightId: String,
  val hotelId: String,
  val carId: String,
)

@RestController
@RequestMapping("/api/v1/bookings")
class BookingController(
  private val bookingService: BookingService,
) {

  @PostMapping
  fun create(@RequestBody request: CreateBookingRequest): Mono<ResponseEntity<BookingEntity>> {
    return bookingService.createBooking(request)
      .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
  }

  @GetMapping("/{bookingId}")
  fun get(@PathVariable bookingId: UUID): Mono<ResponseEntity<BookingEntity>> {
    return bookingService.findById(bookingId)
      .map { ResponseEntity.ok(it) }
      .defaultIfEmpty(ResponseEntity.notFound().build())
  }
}

@Service
class BookingService(
  private val bookingRepository: BookingRepository,
  private val outboxPublisher: OutboxPublisher,
) {
  private val log = LoggerFactory.getLogger(BookingService::class.java)

  @Transactional
  fun createBooking(request: CreateBookingRequest): Mono<BookingEntity> {
    val bookingId = UUID.randomUUID()
    val entity = BookingEntity(
      bookingId = bookingId,
      passengerId = request.passengerId,
      flightId = request.flightId,
      hotelId = request.hotelId,
      carId = request.carId,
      status = BookingStatus.PENDING.name,
      newEntity = true,
    )
    val payload = TravelBookingPayload(
      bookingId = bookingId.toString(),
      passengerId = request.passengerId,
      flightId = request.flightId,
      hotelId = request.hotelId,
      carId = request.carId,
    )
    return bookingRepository.save(entity)
      .flatMap { saved ->
        outboxPublisher.publish(
          partitioningKey = bookingId.toString(),
          eventType = ChoreographyEventTypes.BOOKING_REQUESTED,
          payload = payload,
        ).thenReturn(saved)
      }
      .doOnSuccess { log.info("Booking requested: bookingId={}", bookingId) }
  }

  fun findById(bookingId: UUID): Mono<BookingEntity> = bookingRepository.findById(bookingId)

  @Transactional
  fun markCompleted(payload: TravelBookingPayload): Mono<Void> {
    val bookingId = UUID.fromString(payload.bookingId)
    return bookingRepository.findById(bookingId)
      .flatMap { booking ->
        if (booking.status != BookingStatus.PENDING.name) {
          return@flatMap Mono.empty()
        }
        val updated = booking.copy(
          status = BookingStatus.COMPLETED.name,
          updatedAt = LocalDateTime.now(),
        )
        bookingRepository.save(updated)
          .flatMap {
            outboxPublisher.publish(
              partitioningKey = payload.bookingId,
              eventType = ChoreographyEventTypes.BOOKING_COMPLETED,
              payload = payload,
            )
          }
          .then()
      }
  }

  @Transactional
  fun markFailed(payload: TravelBookingPayload): Mono<Void> {
    val bookingId = UUID.fromString(payload.bookingId)
    return bookingRepository.findById(bookingId)
      .flatMap { booking ->
        if (booking.status != BookingStatus.PENDING.name) {
          return@flatMap Mono.empty()
        }
        val updated = booking.copy(
          status = BookingStatus.FAILED.name,
          errorMessage = payload.errorMessage,
          updatedAt = LocalDateTime.now(),
        )
        bookingRepository.save(updated).then()
      }
  }

  @Transactional
  fun markCompensated(payload: TravelBookingPayload): Mono<Void> {
    val bookingId = UUID.fromString(payload.bookingId)
    return bookingRepository.findById(bookingId)
      .flatMap { booking ->
        if (booking.status != BookingStatus.PENDING.name) {
          return@flatMap Mono.empty()
        }
        val updated = booking.copy(
          status = BookingStatus.COMPENSATED.name,
          errorMessage = payload.errorMessage,
          updatedAt = LocalDateTime.now(),
        )
        bookingRepository.save(updated)
          .flatMap {
            outboxPublisher.publish(
              partitioningKey = payload.bookingId,
              eventType = ChoreographyEventTypes.BOOKING_COMPENSATED,
              payload = payload,
            )
          }
          .then()
      }
  }
}

@Table("bookings")
data class BookingEntity(
  @Id
  @Column("booking_id")
  val bookingId: UUID,
  @Column("passenger_id")
  val passengerId: Long,
  @Column("flight_id")
  val flightId: String,
  @Column("hotel_id")
  val hotelId: String,
  @Column("car_id")
  val carId: String,
  val status: String,
  @Column("error_message")
  val errorMessage: String? = null,
  @Column("created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
  @Column("updated_at")
  val updatedAt: LocalDateTime = LocalDateTime.now(),
  @Transient
  private val newEntity: Boolean = false,
) : Persistable<UUID> {
  override fun getId(): UUID = bookingId
  @Transient
  override fun isNew(): Boolean = newEntity
}

interface BookingRepository : ReactiveCrudRepository<BookingEntity, UUID>

@Component
class CarRentedHandler(
  private val bookingService: BookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.CAR_RENTED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    log.info("Car rented for bookingId={}, completing booking", payload.bookingId)
    return bookingService.markCompleted(payload)
  }
}

@Component
class FlightBookingFailedHandler(
  private val bookingService: BookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.FLIGHT_BOOKING_FAILED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    log.info("Flight booking failed for bookingId={}", payload.bookingId)
    return bookingService.markFailed(payload)
  }
}

@Component
class FlightBookingCancelledHandler(
  private val bookingService: BookingService,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val eventType = ChoreographyEventTypes.FLIGHT_BOOKING_CANCELLED
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    log.info("Flight cancelled (compensation) for bookingId={}", payload.bookingId)
    return bookingService.markCompensated(payload)
  }
}
