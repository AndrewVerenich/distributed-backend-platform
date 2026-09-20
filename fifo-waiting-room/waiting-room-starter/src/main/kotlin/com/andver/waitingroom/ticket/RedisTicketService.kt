package com.andver.waitingroom.ticket

import com.andver.waitingroom.WaitingRoomProperties
import com.andver.waitingroom.metrics.WaitingRoomMetrics
import com.andver.waitingroom.model.TicketPayload
import com.andver.waitingroom.queue.RedisKeys
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.util.UUID

class RedisTicketService(
  private val redis: ReactiveStringRedisTemplate,
  private val properties: WaitingRoomProperties,
  private val metrics: WaitingRoomMetrics,
) : TicketService {

  override fun issue(eventId: String, visitorId: String): Mono<String> {
    val ticketId = UUID.randomUUID().toString()
    val payload = TicketPayload(ticketId, eventId, visitorId).encode()
    return redis.opsForValue()
      .set(RedisKeys.ticket(ticketId), payload, properties.ticketTtl)
      .flatMap { saved ->
        if (saved) {
          metrics.ticketIssued(eventId)
          Mono.just(ticketId)
        } else {
          Mono.error(IllegalStateException("Failed to store opaque ticket $ticketId"))
        }
      }
  }

  override fun consume(ticketId: String): Mono<TicketPayload> {
    if (ticketId.isBlank()) {
      metrics.ticketReplayRejected()
      return Mono.empty()
    }
    return redis.opsForValue()
      .getAndDelete(RedisKeys.ticket(ticketId))
      .map { raw -> TicketPayload.decode(ticketId, raw) }
      .doOnNext { metrics.ticketConsumed() }
      .switchIfEmpty(
        Mono.defer {
          metrics.ticketReplayRejected()
          Mono.empty()
        },
      )
  }
}
