package com.andver.waitingroom.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TicketPayloadTest {

  @Test
  fun `encode and decode round-trip`() {
    val payload = TicketPayload("t-1", "flash-sale-1", "visitor-9")
    val decoded = TicketPayload.decode(payload.ticketId, payload.encode())
    assertEquals(payload, decoded)
  }

  @Test
  fun `decode rejects malformed value`() {
    assertThrows(IllegalArgumentException::class.java) {
      TicketPayload.decode("t-1", "no-separator")
    }
  }
}
