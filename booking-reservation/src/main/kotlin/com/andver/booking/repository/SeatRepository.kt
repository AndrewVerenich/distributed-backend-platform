package com.andver.booking.repository

import com.andver.booking.model.Seat
import com.andver.booking.model.SeatStatus
import io.r2dbc.spi.Readable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class SeatRepository(
  private val databaseClient: DatabaseClient,
) {

  fun findByVenue(venueId: String): Flux<Seat> =
    databaseClient.sql(
      """
      SELECT id, venue_id, label, row_num, col_num, status, holder_id, reservation_id, hold_until
        FROM seats
       WHERE venue_id = :venueId
       ORDER BY row_num, col_num
      """.trimIndent(),
    )
      .bind("venueId", venueId)
      .map { row, _ -> mapSeat(row) }
      .all()

  fun findById(seatId: String): Mono<Seat> =
    databaseClient.sql(
      """
      SELECT id, venue_id, label, row_num, col_num, status, holder_id, reservation_id, hold_until
        FROM seats WHERE id = :seatId
      """.trimIndent(),
    )
      .bind("seatId", seatId)
      .map { row, _ -> mapSeat(row) }
      .one()

  /**
   * Atomic hold: free seat or expired hold becomes ours. Uses DB now() for TTL.
   */
  fun tryHold(
    seatId: String,
    userId: String,
    reservationId: String,
    ttl: Duration,
  ): Mono<Seat> =
    databaseClient.sql(
      """
      UPDATE seats
         SET status = 'held',
             holder_id = :userId,
             reservation_id = :reservationId,
             hold_until = now() + (:ttlSeconds * interval '1 second')
       WHERE id = :seatId
         AND (status = 'free'
              OR (status = 'held' AND hold_until < now()))
      RETURNING id, venue_id, label, row_num, col_num, status, holder_id, reservation_id, hold_until
      """.trimIndent(),
    )
      .bind("seatId", seatId)
      .bind("userId", userId)
      .bind("reservationId", reservationId)
      .bind("ttlSeconds", ttl.seconds)
      .map { row, _ -> mapSeat(row) }
      .one()

  /**
   * Naive path: read then unconditional write — races produce oversell.
   */
  fun naiveHold(
    seatId: String,
    userId: String,
    reservationId: String,
    ttl: Duration,
  ): Mono<Seat> =
    findById(seatId).flatMap { seat ->
      val freeOrExpired =
        seat.status == SeatStatus.FREE ||
          (seat.status == SeatStatus.HELD && seat.holdUntil != null && seat.holdUntil.isBefore(Instant.now()))
      if (!freeOrExpired) {
        return@flatMap Mono.empty()
      }
      databaseClient.sql(
        """
        UPDATE seats
           SET status = 'held',
               holder_id = :userId,
               reservation_id = :reservationId,
               hold_until = now() + (:ttlSeconds * interval '1 second')
         WHERE id = :seatId
        RETURNING id, venue_id, label, row_num, col_num, status, holder_id, reservation_id, hold_until
        """.trimIndent(),
      )
        .bind("seatId", seatId)
        .bind("userId", userId)
        .bind("reservationId", reservationId)
        .bind("ttlSeconds", ttl.seconds)
        .map { row, _ -> mapSeat(row) }
        .one()
    }

  fun tryConfirm(seatId: String, reservationId: String): Mono<Seat> =
    databaseClient.sql(
      """
      UPDATE seats
         SET status = 'sold',
             hold_until = NULL
       WHERE id = :seatId
         AND status = 'held'
         AND reservation_id = :reservationId
         AND hold_until >= now()
      RETURNING id, venue_id, label, row_num, col_num, status, holder_id, reservation_id, hold_until
      """.trimIndent(),
    )
      .bind("seatId", seatId)
      .bind("reservationId", reservationId)
      .map { row, _ -> mapSeat(row) }
      .one()

  fun releaseExpiredHolds(): Flux<ReleasedHold> =
    databaseClient.sql(
      """
      UPDATE seats
         SET status = 'free',
             holder_id = NULL,
             reservation_id = NULL,
             hold_until = NULL
       WHERE status = 'held'
         AND hold_until < now()
      RETURNING id, reservation_id
      """.trimIndent(),
    )
      .map { row, _ ->
        ReleasedHold(
          seatId = row.get("id", String::class.java)!!,
          reservationId = row.get("reservation_id", String::class.java),
        )
      }
      .all()

  fun resetVenue(venueId: String): Mono<Long> =
    databaseClient.sql(
      """
      UPDATE seats
         SET status = 'free',
             holder_id = NULL,
             reservation_id = NULL,
             hold_until = NULL
       WHERE venue_id = :venueId
      """.trimIndent(),
    )
      .bind("venueId", venueId)
      .fetch()
      .rowsUpdated()

  private fun mapSeat(row: Readable): Seat =
    Seat(
      id = row.get("id", String::class.java)!!,
      venueId = row.get("venue_id", String::class.java)!!,
      label = row.get("label", String::class.java)!!,
      rowNum = row.get("row_num", Integer::class.java)!!.toInt(),
      colNum = row.get("col_num", Integer::class.java)!!.toInt(),
      status = SeatStatus.fromDb(row.get("status", String::class.java)!!),
      holderId = row.get("holder_id", String::class.java),
      reservationId = row.get("reservation_id", String::class.java),
      holdUntil = row.get("hold_until")?.let { toInstant(it) },
    )

  private fun toInstant(value: Any): Instant =
    when (value) {
      is Instant -> value
      is OffsetDateTime -> value.toInstant()
      else -> error("Unsupported timestamp: ${value.javaClass}")
    }

  data class ReleasedHold(val seatId: String, val reservationId: String?)
}
