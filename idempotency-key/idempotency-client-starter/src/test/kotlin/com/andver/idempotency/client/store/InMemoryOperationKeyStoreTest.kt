package com.andver.idempotency.client.store

import com.andver.idempotency.client.IdempotencyClientProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class InMemoryOperationKeyStoreTest {

  @Test
  fun `same operationId keeps the same key`() {
    val store = InMemoryOperationKeyStore(
      IdempotencyClientProperties(operationTtl = Duration.ofHours(1)),
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    val first = store.resolve("pay-42")
    val second = store.resolve("pay-42")
    assertEquals(first, second)
    assertNotEquals(first, store.resolve("pay-43"))
  }
}
