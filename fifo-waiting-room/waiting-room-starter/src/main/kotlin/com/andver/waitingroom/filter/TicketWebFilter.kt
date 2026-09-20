package com.andver.waitingroom.filter

import com.andver.waitingroom.WaitingRoomProperties
import com.andver.waitingroom.model.TicketPayload
import com.andver.waitingroom.ticket.TicketService
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

object TicketAttributes {
  const val PAYLOAD = "waitingRoom.ticketPayload"
}

/**
 * Consumes an opaque admission ticket from `Authorization: Ticket <id>`
 * for configured path prefixes. Single-use: Redis GETDEL.
 */
class TicketWebFilter(
  private val ticketService: TicketService,
  private val properties: WaitingRoomProperties,
) : WebFilter, Ordered {

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 50

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val path = exchange.request.path.value()
    if (!properties.ticketFilter.pathPrefixes.any { path.startsWith(it) }) {
      return chain.filter(exchange)
    }

    val ticketId = extractTicket(exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
    if (ticketId == null) {
      return unauthorized(exchange, "Missing Authorization: Ticket <opaqueId>")
    }

    return ticketService.consume(ticketId)
      .flatMap { payload ->
        exchange.attributes[TicketAttributes.PAYLOAD] = payload
        chain.filter(exchange)
      }
      .switchIfEmpty(unauthorized(exchange, "Invalid or already used ticket"))
  }

  private fun extractTicket(header: String?): String? {
    if (header.isNullOrBlank()) return null
    val prefix = "Ticket "
    if (!header.startsWith(prefix, ignoreCase = true)) return null
    return header.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
  }

  private fun unauthorized(exchange: ServerWebExchange, message: String): Mono<Void> {
    val response = exchange.response
    response.statusCode = HttpStatus.UNAUTHORIZED
    response.headers.contentType = MediaType.APPLICATION_JSON
    val body = response.bufferFactory().wrap("""{"error":"$message"}""".toByteArray())
    return response.writeWith(Mono.just(body))
  }

  companion object {
    fun payload(exchange: ServerWebExchange): TicketPayload? =
      exchange.getAttribute(TicketAttributes.PAYLOAD)
  }
}
