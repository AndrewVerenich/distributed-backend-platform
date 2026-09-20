package com.andver.waitingroom.filter

import com.andver.waitingroom.WaitingRoomProperties
import com.andver.waitingroom.model.TicketPayload
import com.andver.waitingroom.ticket.TicketService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class TicketWebFilterTest {

  private val tickets = mockk<TicketService>()
  private val properties = WaitingRoomProperties(
    ticketFilter = WaitingRoomProperties.TicketFilterProperties(
      enabled = true,
      pathPrefixes = listOf("/checkout"),
    ),
  )
  private val filter = TicketWebFilter(tickets, properties)

  @Test
  fun `passes through non-protected paths`() {
    val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/health").build())
    val chain = WebFilterChain { Mono.empty() }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
    verify(exactly = 0) { tickets.consume(any()) }
  }

  @Test
  fun `rejects missing ticket header`() {
    val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/checkout").build())
    val chain = WebFilterChain { Mono.empty() }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
    assert(exchange.response.statusCode == HttpStatus.UNAUTHORIZED)
  }

  @Test
  fun `consumes valid ticket and continues`() {
    val payload = TicketPayload("abc", "flash-sale-1", "v1")
    every { tickets.consume("abc") } returns Mono.just(payload)

    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.post("/checkout")
        .header(HttpHeaders.AUTHORIZATION, "Ticket abc")
        .build(),
    )
    val chain = WebFilterChain { Mono.empty() }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
    assert(TicketWebFilter.payload(exchange) == payload)
  }

  @Test
  fun `rejects replayed ticket`() {
    every { tickets.consume("used") } returns Mono.empty()

    val exchange = MockServerWebExchange.from(
      MockServerHttpRequest.post("/checkout")
        .header(HttpHeaders.AUTHORIZATION, "Ticket used")
        .build(),
    )
    val chain = WebFilterChain { Mono.empty() }

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
    assert(exchange.response.statusCode == HttpStatus.UNAUTHORIZED)
  }
}
