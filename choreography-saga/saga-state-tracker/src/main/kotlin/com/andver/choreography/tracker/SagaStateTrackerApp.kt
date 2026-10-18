package com.andver.choreography.tracker

import com.andver.choreography.model.ChoreographyEventTypes
import com.andver.choreography.model.TravelBookingPayload
import com.andver.inbox.handler.AbstractInboxEventHandler
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@SpringBootApplication
@EnableR2dbcRepositories
@EnableScheduling
class SagaStateTrackerApp

fun main(args: Array<String>) {
  SpringApplication.run(SagaStateTrackerApp::class.java, *args)
}

enum class SagaProjectionStatus {
  IN_PROGRESS,
  COMPLETED,
  FAILED,
  COMPENSATED,
  STUCK,
}

@Service
class SagaProjectionService(
  private val projectionRepository: SagaProjectionRepository,
  private val eventLogRepository: SagaEventLogRepository,
  private val objectMapper: ObjectMapper,
  private val meterRegistry: MeterRegistry,
) {
  private val log = LoggerFactory.getLogger(SagaProjectionService::class.java)

  @Transactional
  fun applyEvent(
    eventType: String,
    payload: TravelBookingPayload,
    idempotencyKey: String,
  ): Mono<Void> {
    val bookingId = UUID.fromString(payload.bookingId)
    val now = LocalDateTime.now()
    val status = mapStatus(eventType)

    return eventLogRepository.insertIfAbsent(
      bookingId = bookingId,
      eventType = eventType,
      idempotencyKey = idempotencyKey,
      payload = objectMapper.writeValueAsString(payload),
    )
      .then(projectionRepository.findById(bookingId))
      .flatMap { existing ->
        if (isTerminal(existing.status) && status == SagaProjectionStatus.IN_PROGRESS.name) {
          return@flatMap Mono.just(existing)
        }
        val updated = existing.copy(
          status = preferStatus(existing.status, status),
          lastEventType = eventType,
          lastEventAt = now,
          flightBookingId = payload.flightBookingId?.let(UUID::fromString) ?: existing.flightBookingId,
          hotelBookingId = payload.hotelBookingId?.let(UUID::fromString) ?: existing.hotelBookingId,
          carRentalId = payload.carRentalId?.let(UUID::fromString) ?: existing.carRentalId,
          errorMessage = payload.errorMessage ?: existing.errorMessage,
          updatedAt = now,
        )
        projectionRepository.save(updated)
      }
      .switchIfEmpty(
        Mono.defer {
          projectionRepository.save(
            SagaProjectionEntity(
              bookingId = bookingId,
              status = status,
              lastEventType = eventType,
              lastEventAt = now,
              flightBookingId = payload.flightBookingId?.let(UUID::fromString),
              hotelBookingId = payload.hotelBookingId?.let(UUID::fromString),
              carRentalId = payload.carRentalId?.let(UUID::fromString),
              errorMessage = payload.errorMessage,
              newEntity = true,
            )
          )
        }
      )
      .doOnSuccess {
        log.debug("Projected event={} bookingId={} status={}", eventType, bookingId, status)
      }
      .then()
  }

  fun findAll(status: String?): Flux<SagaProjectionEntity> {
    return if (status.isNullOrBlank()) {
      projectionRepository.findAll()
    } else {
      projectionRepository.findByStatus(status)
    }
  }

  fun findById(bookingId: UUID): Mono<SagaProjectionEntity> = projectionRepository.findById(bookingId)

  fun findEvents(bookingId: UUID): Flux<SagaEventLogEntity> =
    eventLogRepository.findByBookingIdOrderByReceivedAtAsc(bookingId)

  fun markStuck(hangAfterSeconds: Long): Mono<Long> {
    val threshold = LocalDateTime.now().minusSeconds(hangAfterSeconds)
    return projectionRepository.findStuckCandidates(threshold)
      .flatMap { saga ->
        val stuck = saga.copy(
          status = SagaProjectionStatus.STUCK.name,
          updatedAt = LocalDateTime.now(),
        )
        projectionRepository.save(stuck)
          .doOnSuccess {
            meterRegistry.counter("saga.stuck").increment()
            log.warn(
              "Saga marked STUCK: bookingId={} lastEvent={} lastAt={}",
              saga.bookingId,
              saga.lastEventType,
              saga.lastEventAt,
            )
          }
      }
      .count()
  }

  private fun mapStatus(eventType: String): String = when (eventType) {
    ChoreographyEventTypes.BOOKING_COMPLETED -> SagaProjectionStatus.COMPLETED.name
    ChoreographyEventTypes.BOOKING_COMPENSATED -> SagaProjectionStatus.COMPENSATED.name
    ChoreographyEventTypes.FLIGHT_BOOKING_FAILED,
    ChoreographyEventTypes.CAR_RENTAL_FAILED,
    -> SagaProjectionStatus.FAILED.name
    else -> SagaProjectionStatus.IN_PROGRESS.name
  }

  private fun isTerminal(status: String): Boolean =
    status == SagaProjectionStatus.COMPLETED.name ||
      status == SagaProjectionStatus.COMPENSATED.name ||
      status == SagaProjectionStatus.FAILED.name

  private fun preferStatus(current: String, incoming: String): String {
    if (isTerminal(current) && incoming == SagaProjectionStatus.IN_PROGRESS.name) {
      return current
    }
    if (incoming == SagaProjectionStatus.STUCK.name) {
      return current
    }
    return incoming
  }
}

@Component
class HangDetector(
  private val sagaProjectionService: SagaProjectionService,
  @Value("\${tracker.hang-after-seconds:30}") private val hangAfterSeconds: Long,
) {
  private val log = LoggerFactory.getLogger(HangDetector::class.java)

  @Scheduled(fixedDelayString = "\${tracker.hang-check-interval-ms:10000}")
  fun detect() {
    sagaProjectionService.markStuck(hangAfterSeconds)
      .doOnNext { count ->
        if (count > 0) {
          log.info("Hang detector marked {} sagas as STUCK", count)
        }
      }
      .subscribe()
  }
}

@RestController
@RequestMapping("/api/v1/sagas")
class SagaTrackerController(
  private val sagaProjectionService: SagaProjectionService,
) {

  @GetMapping
  fun list(@RequestParam(required = false) status: String?): Flux<SagaProjectionEntity> {
    return sagaProjectionService.findAll(status)
  }

  @GetMapping("/{bookingId}")
  fun get(@PathVariable bookingId: UUID): Mono<ResponseEntity<SagaDetailResponse>> {
    return sagaProjectionService.findById(bookingId)
      .flatMap { projection ->
        sagaProjectionService.findEvents(bookingId)
          .collectList()
          .map { events ->
            ResponseEntity.ok(SagaDetailResponse(projection = projection, events = events))
          }
      }
      .defaultIfEmpty(ResponseEntity.notFound().build())
  }
}

data class SagaDetailResponse(
  val projection: SagaProjectionEntity,
  val events: List<SagaEventLogEntity>,
)

@Table("saga_projection")
data class SagaProjectionEntity(
  @Id
  @Column("booking_id")
  val bookingId: UUID,
  val status: String,
  @Column("last_event_type")
  val lastEventType: String,
  @Column("last_event_at")
  val lastEventAt: LocalDateTime,
  @Column("flight_booking_id")
  val flightBookingId: UUID? = null,
  @Column("hotel_booking_id")
  val hotelBookingId: UUID? = null,
  @Column("car_rental_id")
  val carRentalId: UUID? = null,
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

@Table("saga_event_log")
data class SagaEventLogEntity(
  @Id
  val id: Long? = null,
  @Column("booking_id")
  val bookingId: UUID,
  @Column("event_type")
  val eventType: String,
  @Column("idempotency_key")
  val idempotencyKey: UUID,
  val payload: String,
  @Column("received_at")
  val receivedAt: LocalDateTime = LocalDateTime.now(),
)

interface SagaProjectionRepository : ReactiveCrudRepository<SagaProjectionEntity, UUID> {
  fun findByStatus(status: String): Flux<SagaProjectionEntity>

  @Query(
    """
    SELECT * FROM saga_projection
    WHERE status = 'IN_PROGRESS' AND last_event_at < :threshold
    """
  )
  fun findStuckCandidates(threshold: LocalDateTime): Flux<SagaProjectionEntity>
}

interface SagaEventLogRepository : ReactiveCrudRepository<SagaEventLogEntity, Long> {
  fun findByBookingIdOrderByReceivedAtAsc(bookingId: UUID): Flux<SagaEventLogEntity>

  @Query(
    """
    INSERT INTO saga_event_log (booking_id, event_type, idempotency_key, payload)
    VALUES (:bookingId, :eventType, CAST(:idempotencyKey AS UUID), CAST(:payload AS jsonb))
    ON CONFLICT (idempotency_key) DO NOTHING
    """
  )
  fun insertIfAbsent(
    bookingId: UUID,
    eventType: String,
    idempotencyKey: String,
    payload: String,
  ): Mono<Void>
}

abstract class TrackingHandler(
  private val sagaProjectionService: SagaProjectionService,
  override val eventType: String,
) : AbstractInboxEventHandler<TravelBookingPayload>() {
  override val payloadType = TravelBookingPayload::class.java

  override fun handle(payload: TravelBookingPayload, idempotencyKey: String): Mono<Void> {
    return sagaProjectionService.applyEvent(eventType, payload, idempotencyKey)
  }
}

@Component
class BookingRequestedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.BOOKING_REQUESTED)

@Component
class FlightBookedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.FLIGHT_BOOKED)

@Component
class FlightBookingFailedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.FLIGHT_BOOKING_FAILED)

@Component
class FlightBookingCancelledTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.FLIGHT_BOOKING_CANCELLED)

@Component
class HotelBookedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.HOTEL_BOOKED)

@Component
class HotelBookingFailedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.HOTEL_BOOKING_FAILED)

@Component
class CarRentedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.CAR_RENTED)

@Component
class CarRentalFailedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.CAR_RENTAL_FAILED)

@Component
class BookingCompletedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.BOOKING_COMPLETED)

@Component
class BookingCompensatedTracker(svc: SagaProjectionService) :
  TrackingHandler(svc, ChoreographyEventTypes.BOOKING_COMPENSATED)
