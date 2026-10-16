package com.andver.booking.payment

import com.andver.booking.api.PaymentResponse
import com.andver.booking.repository.ReservationRepository
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

@Service
class PaymentService(
  private val databaseClient: DatabaseClient,
  private val reservations: ReservationRepository,
) {

  /**
   * Demo payment: sleeps [delayMs] then marks paid. No external provider.
   */
  fun pay(reservationId: String, delayMs: Long): Mono<PaymentResponse> {
    val paymentId = UUID.randomUUID().toString()
    return reservations.findById(reservationId)
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "reservation not found")))
      .flatMap {
        insertPending(paymentId, reservationId, delayMs)
          .then(
            Mono.delay(Duration.ofMillis(delayMs.coerceAtLeast(0)))
              .then(markPaid(paymentId, reservationId)),
          )
      }
  }

  private fun insertPending(paymentId: String, reservationId: String, delayMs: Long): Mono<Void> =
    databaseClient.sql(
      """
      INSERT INTO payments (id, reservation_id, status, delay_ms, created_at)
      VALUES (:id, :reservationId, 'pending', :delayMs, now())
      """.trimIndent(),
    )
      .bind("id", paymentId)
      .bind("reservationId", reservationId)
      .bind("delayMs", delayMs.toInt())
      .fetch()
      .rowsUpdated()
      .then()

  private fun markPaid(paymentId: String, reservationId: String): Mono<PaymentResponse> =
    databaseClient.sql(
      """
      UPDATE payments
         SET status = 'paid', completed_at = now()
       WHERE id = :id
      """.trimIndent(),
    )
      .bind("id", paymentId)
      .fetch()
      .rowsUpdated()
      .thenReturn(
        PaymentResponse(
          paymentId = paymentId,
          reservationId = reservationId,
          status = "paid",
        ),
      )
}
