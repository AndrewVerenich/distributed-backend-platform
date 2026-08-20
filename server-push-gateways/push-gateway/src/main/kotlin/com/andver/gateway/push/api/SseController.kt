package com.andver.gateway.push.api

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.gateway.push.service.ConnectionGate
import com.andver.gateway.push.service.EventFanoutService
import com.andver.gateway.push.service.EventReplayService
import com.andver.gateway.push.service.SseConnectionManager
import com.andver.push.model.PushEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@RequestMapping("/sse")
class SseController(
  private val connectionGate: ConnectionGate,
  private val sseConnectionManager: SseConnectionManager,
  private val eventReplayService: EventReplayService,
  private val eventFanoutService: EventFanoutService,
  private val properties: PushGatewayProperties,
  private val metrics: DeliveryMetrics,
  private val objectMapper: ObjectMapper,
) {

  @GetMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun stream(
    @RequestParam clientId: String,
    @RequestHeader(value = "Last-Event-ID", required = false) lastEventIdHeader: String?,
  ): Flux<ServerSentEvent<String>> {
    require(clientId.isNotBlank()) { "clientId is required" }
    connectionGate.assertAcceptable()

    val lastEventId = lastEventIdHeader?.toLongOrNull() ?: 0L
    if (lastEventId > 0) {
      metrics.recordReconnect("sse")
    }

    eventFanoutService.ensureSubscribed(clientId)
    val session = sseConnectionManager.register(clientId)

    val replay = eventReplayService.findAfterLastEventId(clientId, lastEventId)
      .flatMapMany { events ->
        Flux.fromIterable(events)
          .doOnNext { metrics.recordReplayed("sse", "last-event-id") }
          .map { toSse(it) }
      }

    val live = session.sink.asFlux()
      .map { toSse(it) }

    val heartbeat = Flux.interval(properties.sseHeartbeatInterval)
      .map {
        ServerSentEvent.builder<String>()
          .comment("keepalive")
          .build()
      }

    return Flux.merge(replay, live, heartbeat)
      .doFinally {
        sseConnectionManager.unregister(session)
        eventFanoutService.maybeUnsubscribe(clientId)
      }
  }

  private fun toSse(event: PushEvent): ServerSentEvent<String> {
    val data = objectMapper.writeValueAsString(event)
    return ServerSentEvent.builder<String>()
      .id(event.eventId.toString())
      .data(data)
      .build()
  }
}
