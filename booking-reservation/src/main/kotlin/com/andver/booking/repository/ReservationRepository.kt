package com.andver.booking.repository

import com.andver.booking.model.Reservation
import com.andver.booking.model.ReservationStatus
import io.r2dbc.spi.Readable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class ReservationRepository(
  private val databaseClient: DatabaseClient,
) {

  fun insert(
    id: String,
    seatId: String,
    userId: String,
    holdUntil: Instant?,
  ): Mono<Reservation> =
    databaseClient.sql(
      """
      INSERT INTO reservations (id, seat_id, user_id, status, hold_until, created_at)
      VALUES (:id, :seatId, :userId, 'held', :holdUntil, now())
      RETURNING id, seat_id, user_id, status, hold_until, created_at, confirmed_at
      """.trimIndent(),
    )
      .bind("id", id)
      .bind("seatId", seatId)
      .bind("userId", userId)
      .let { spec ->
        if (holdUntil != null) {
          spec.bind("holdUntil", holdUntil.atOffset(ZoneOffset.UTC))
        } else {
          spec.bindNull("holdUntil", OffsetDateTime::class.java)
        }
      }
      .map { row, _ -> mapReservation(row) }
      .one()

  fun findById(id: String): Mono<Reservation> =
    databaseClient.sql(
      """
      SELECT id, seat_id, user_id, status, hold_until, created_at, confirmed_at
        FROM reservations WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", id)
      .map { row, _ -> mapReservation(row) }
      .one()

  fun markConfirmed(id: String): Mono<Reservation> =
    databaseClient.sql(
      """
      UPDATE reservations
         SET status = 'confirmed', confirmed_at = now()
       WHERE id = :id AND status = 'held'
      RETURNING id, seat_id, user_id, status, hold_until, created_at, confirmed_at
      """.trimIndent(),
    )
      .bind("id", id)
      .map { row, _ -> mapReservation(row) }
      .one()

  fun markExpired(ids: Collection<String>): Mono<Long> {
    if (ids.isEmpty()) return Mono.just(0)
    val idList = ids.joinToString(",") { "'${it.replace("'", "''")}'" }
    return databaseClient.sql(
      """
      UPDATE reservations
         SET status = 'expired'
       WHERE id IN ($idList)
         AND status = 'held'
      """.trimIndent(),
    )
      .fetch()
      .rowsUpdated()
  }

  fun markCompensated(id: String): Mono<Reservation> =
    databaseClient.sql(
      """
      UPDATE reservations
         SET status = 'compensated'
       WHERE id = :id
      RETURNING id, seat_id, user_id, status, hold_until, created_at, confirmed_at
      """.trimIndent(),
    )
      .bind("id", id)
      .map { row, _ -> mapReservation(row) }
      .one()

  fun countActiveForSeat(seatId: String): Mono<Long> =
    databaseClient.sql(
      """
      SELECT COUNT(*) AS cnt
        FROM reservations
       WHERE seat_id = :seatId
         AND status IN ('held', 'confirmed')
      """.trimIndent(),
    )
      .bind("seatId", seatId)
      .map { row, _ -> row.get("cnt", java.lang.Long::class.java)!!.toLong() }
      .one()

  fun deleteByVenue(venueId: String): Mono<Long> =
    databaseClient.sql(
      """
      DELETE FROM reservations
       WHERE seat_id IN (SELECT id FROM seats WHERE venue_id = :venueId)
      """.trimIndent(),
    )
      .bind("venueId", venueId)
      .fetch()
      .rowsUpdated()

  private fun mapReservation(row: Readable): Reservation =
    Reservation(
      id = row.get("id", String::class.java)!!,
      seatId = row.get("seat_id", String::class.java)!!,
      userId = row.get("user_id", String::class.java)!!,
      status = ReservationStatus.fromDb(row.get("status", String::class.java)!!),
      holdUntil = row.get("hold_until")?.let { toInstant(it) },
      createdAt = toInstant(row.get("created_at")!!),
      confirmedAt = row.get("confirmed_at")?.let { toInstant(it) },
    )

  private fun toInstant(value: Any): Instant =
    when (value) {
      is Instant -> value
      is OffsetDateTime -> value.toInstant()
      else -> error("Unsupported timestamp: ${value.javaClass}")
    }
}
