package com.andver.waitingroom.service.controller

import com.andver.waitingroom.model.EventMeta
import com.andver.waitingroom.model.JoinOutcome
import com.andver.waitingroom.model.QueueSnapshot
import com.andver.waitingroom.queue.FifoQueueService
import com.andver.waitingroom.service.model.ErrorResponse
import com.andver.waitingroom.service.model.EventMetaRequest
import com.andver.waitingroom.service.model.JoinRequest
import com.andver.waitingroom.service.model.QueueResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/events")
class WaitingRoomController(
  private val queue: FifoQueueService,
) {

  @PostMapping("/{eventId}/join")
  fun join(
    @PathVariable eventId: String,
    @RequestBody(required = false) body: JoinRequest?,
  ): Mono<ResponseEntity<*>> {
    val visitorId = body?.visitorId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    return queue.join(eventId, visitorId).map { outcome ->
      when (outcome) {
        is JoinOutcome.Ok -> ResponseEntity.ok(toResponse(outcome.snapshot))
        is JoinOutcome.Rejected -> ResponseEntity.status(HttpStatus.CONFLICT)
          .body(ErrorResponse("join rejected", outcome.reason.name))
      }
    }
  }

  @GetMapping("/{eventId}/status/{visitorId}")
  fun status(
    @PathVariable eventId: String,
    @PathVariable visitorId: String,
  ): Mono<QueueResponse> =
    queue.status(eventId, visitorId).map(::toResponse)

  @DeleteMapping("/{eventId}/leave/{visitorId}")
  fun leave(
    @PathVariable eventId: String,
    @PathVariable visitorId: String,
  ): Mono<ResponseEntity<Map<String, Any>>> =
    queue.leave(eventId, visitorId).map { removed ->
      ResponseEntity.ok(mapOf("removed" to removed, "visitorId" to visitorId, "eventId" to eventId))
    }

  @PutMapping("/{eventId}/meta")
  fun updateMeta(
    @PathVariable eventId: String,
    @RequestBody body: EventMetaRequest,
  ): Mono<EventMeta> =
    queue.updateMeta(
      EventMeta(
        eventId = eventId,
        admitRate = body.admitRate.coerceAtLeast(1),
        maxQueue = body.maxQueue.coerceAtLeast(1),
        open = body.open,
      ),
    )

  @GetMapping("/{eventId}/meta")
  fun getMeta(@PathVariable eventId: String): Mono<EventMeta> = queue.getMeta(eventId)

  private fun toResponse(snapshot: QueueSnapshot) = QueueResponse(
    eventId = snapshot.eventId,
    visitorId = snapshot.visitorId,
    position = snapshot.position,
    etaSeconds = snapshot.etaSeconds,
    status = snapshot.status,
    ticket = snapshot.ticket,
  )
}
