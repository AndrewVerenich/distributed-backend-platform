package com.andver.waitingroom.queue

import com.andver.waitingroom.model.EventMeta
import com.andver.waitingroom.model.JoinOutcome
import com.andver.waitingroom.model.QueueSnapshot
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface FifoQueueService {
  fun join(eventId: String, visitorId: String): Mono<JoinOutcome>
  fun status(eventId: String, visitorId: String): Mono<QueueSnapshot>
  fun leave(eventId: String, visitorId: String): Mono<Boolean>
  fun updateMeta(meta: EventMeta): Mono<EventMeta>
  fun getMeta(eventId: String): Mono<EventMeta>
  fun queueDepth(eventId: String): Mono<Long>
  fun listEventIds(): Flux<String>
  /** Pop up to [limit] visitors from the head (caller issues tickets). */
  fun popAdmitted(eventId: String, limit: Int): Flux<String>
  fun markAdmitted(eventId: String, visitorId: String, ticketId: String): Mono<Long>
  fun tryAcquireAdmitLock(eventId: String): Mono<Boolean>
  fun releaseAdmitLock(eventId: String): Mono<Boolean>
}
