package com.andver.idempotency.demo.charge.repository

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class LedgerRepository(
  private val databaseClient: DatabaseClient,
) {

  fun debit(ownerId: String, amount: Long, chargeId: UUID): Mono<Long> =
    databaseClient.sql(
      """
      UPDATE accounts
         SET balance = balance - :amount
       WHERE owner_id = :ownerId
         AND balance >= :amount
      RETURNING balance
      """.trimIndent(),
    )
      .bind("ownerId", ownerId)
      .bind("amount", amount)
      .map { row, _ -> row.get("balance", java.lang.Long::class.java)!!.toLong() }
      .one()
      .flatMap { balance ->
        databaseClient.sql(
          """
          INSERT INTO charges (id, owner_id, amount)
          VALUES (:id, :ownerId, :amount)
          """.trimIndent(),
        )
          .bind("id", chargeId)
          .bind("ownerId", ownerId)
          .bind("amount", amount)
          .fetch()
          .rowsUpdated()
          .thenReturn(balance)
      }

  fun countCharges(ownerId: String): Mono<Long> =
    databaseClient.sql("SELECT COUNT(*) AS cnt FROM charges WHERE owner_id = :ownerId")
      .bind("ownerId", ownerId)
      .map { row, _ -> row.get("cnt", java.lang.Long::class.java)!!.toLong() }
      .one()

  fun balance(ownerId: String): Mono<Long> =
    databaseClient.sql("SELECT balance FROM accounts WHERE owner_id = :ownerId")
      .bind("ownerId", ownerId)
      .map { row, _ -> row.get("balance", java.lang.Long::class.java)!!.toLong() }
      .one()

  fun reset(ownerId: String, balance: Long): Mono<Void> =
    databaseClient.sql("DELETE FROM charges WHERE owner_id = :ownerId")
      .bind("ownerId", ownerId)
      .fetch()
      .rowsUpdated()
      .then(
        databaseClient.sql("DELETE FROM idempotency_records WHERE owner_id = :ownerId")
          .bind("ownerId", ownerId)
          .fetch()
          .rowsUpdated(),
      )
      .then(
        databaseClient.sql(
          """
          INSERT INTO accounts (owner_id, balance) VALUES (:ownerId, :balance)
          ON CONFLICT (owner_id) DO UPDATE SET balance = EXCLUDED.balance
          """.trimIndent(),
        )
          .bind("ownerId", ownerId)
          .bind("balance", balance)
          .fetch()
          .rowsUpdated(),
      )
      .then()
}
