package com.andver.idempotency.client.store

import com.andver.idempotency.client.IdempotencyClientProperties
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryOperationKeyStore(
  private val properties: IdempotencyClientProperties,
  private val clock: Clock = Clock.systemUTC(),
) : OperationKeyStore {

  private val keys = ConcurrentHashMap<String, Entry>()

  override fun resolve(operationId: String): String {
    val now = clock.instant()
    val entry = keys.compute(operationId) { _, existing ->
      if (existing != null && existing.expiresAt.isAfter(now)) {
        existing
      } else {
        Entry(UUID.randomUUID().toString(), now.plus(properties.operationTtl))
      }
    }!!
    return entry.key
  }

  fun peek(operationId: String): String? {
    val entry = keys[operationId] ?: return null
    return if (entry.expiresAt.isAfter(clock.instant())) entry.key else null
  }

  private data class Entry(val key: String, val expiresAt: Instant)
}
