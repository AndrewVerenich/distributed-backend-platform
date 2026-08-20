package com.andver.gateway.push.api

import com.andver.gateway.push.config.PushGatewayProperties
import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.gateway.push.service.ConnectionGate
import com.andver.gateway.push.service.EventFanoutService
import com.andver.gateway.push.service.EventReplayService
import com.andver.gateway.push.service.PendingPollRegistry
import com.andver.push.model.PushEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/poll")
class LongPollController(
  private val connectionGate: ConnectionGate,
  private val eventReplayService: EventReplayService,
  private val pendingPollRegistry: PendingPollRegistry,
  private val eventFanoutService: EventFanoutService,
  private val properties: PushGatewayProperties,
  private val metrics: DeliveryMetrics,
) {

  @GetMapping("/updates")
  fun poll(
    @RequestParam clientId: String,
    @RequestParam(defaultValue = "0") since: Long,
  ): Mono<List<PushEvent>> {
    require(clientId.isNotBlank()) { "clientId is required" }
    connectionGate.assertAcceptable()

    if (since > 0) {
      metrics.recordReconnect("poll")
    }

    eventFanoutService.ensureSubscribed(clientId)

    return eventReplayService.findSince(clientId, since)
      .flatMap { existing ->
        if (existing.isNotEmpty()) {
          existing.forEach { metrics.recordReplayed("poll", "since-cursor") }
          Mono.just(existing)
        } else {
          pendingPollRegistry.registerMono(clientId)
            .timeout(properties.longPollTimeout, Mono.just(emptyList()))
        }
      }
      .doFinally {
        eventFanoutService.maybeUnsubscribe(clientId)
      }
  }
}
