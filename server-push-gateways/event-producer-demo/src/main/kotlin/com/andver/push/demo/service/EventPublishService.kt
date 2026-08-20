package com.andver.push.demo.service

import com.andver.push.demo.api.BurstEventRequest
import com.andver.push.demo.api.PublishEventRequest
import com.andver.push.model.PushEvent
import com.andver.push.sender.PushEventSender
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

interface EventPublishService {
  fun publish(request: PublishEventRequest): Mono<Unit>
  fun burst(request: BurstEventRequest): Mono<Unit>
}

@Service
class DefaultEventPublishService(
  private val pushEventSender: PushEventSender,
) : EventPublishService {

  override fun publish(request: PublishEventRequest): Mono<Unit> {
    val event = PushEvent(
      clientId = request.clientId,
      type = request.type,
      payload = request.payload,
      publishedAt = Instant.now(),
    )
    return pushEventSender.send(event)
  }

  override fun burst(request: BurstEventRequest): Mono<Unit> {
    return Flux.range(1, request.count)
      .concatMap { index ->
        val event = PushEvent(
          clientId = request.clientId,
          type = request.type,
          payload = mapOf(
            "index" to index,
            "total" to request.count,
          ),
          publishedAt = Instant.now(),
        )
        val send = pushEventSender.send(event)
        if (request.intervalMs > 0 && index < request.count) {
          send.then(Mono.delay(Duration.ofMillis(request.intervalMs))).thenReturn(Unit)
        } else {
          send
        }
      }
      .then(Mono.just(Unit))
  }
}
