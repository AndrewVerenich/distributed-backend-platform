package com.andver.waitingroom.ticket

import com.andver.waitingroom.model.TicketPayload
import reactor.core.publisher.Mono

interface TicketService {
  fun issue(eventId: String, visitorId: String): Mono<String>
  fun consume(ticketId: String): Mono<TicketPayload>
}
