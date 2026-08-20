package com.andver.push.demo.api

import com.andver.push.demo.service.EventPublishService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/events")
class EventController(
  private val service: EventPublishService,
) {

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun publish(@RequestBody request: PublishEventRequest): Mono<Map<String, Any>> {
    require(request.clientId.isNotBlank()) { "clientId is required" }
    return service.publish(request).thenReturn(
      mapOf(
        "status" to "accepted",
        "clientId" to request.clientId,
        "type" to request.type,
      ),
    )
  }

  @PostMapping("/burst")
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun burst(@RequestBody request: BurstEventRequest): Mono<Map<String, Any>> {
    require(request.clientId.isNotBlank()) { "clientId is required" }
    require(request.count in 1..10_000) { "count must be between 1 and 10000" }
    return service.burst(request).thenReturn(
      mapOf(
        "status" to "accepted",
        "clientId" to request.clientId,
        "count" to request.count,
        "intervalMs" to request.intervalMs,
      ),
    )
  }
}
