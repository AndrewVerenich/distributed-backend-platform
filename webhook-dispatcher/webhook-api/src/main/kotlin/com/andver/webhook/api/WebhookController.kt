package com.andver.webhook.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping
class WebhookController(
  private val service: WebhookService,
) {
  @PostMapping("/webhooks")
  fun create(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @RequestBody req: CreateWebhookRequest,
  ): Mono<ResponseEntity<WebhookResponse>> =
    service.requireTenant(apiKey)
      .flatMap { service.createWebhook(it.id, req) }
      .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }

  @GetMapping("/webhooks")
  fun list(@RequestHeader("X-Api-Key") apiKey: String?): Flux<WebhookResponse> =
    service.requireTenant(apiKey).flatMapMany { service.listWebhooks(it.id) }

  @GetMapping("/webhooks/{id}")
  fun get(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @PathVariable id: String,
  ): Mono<WebhookResponse> =
    service.requireTenant(apiKey).flatMap { service.getWebhook(it.id, id) }

  @DeleteMapping("/webhooks/{id}")
  fun delete(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @PathVariable id: String,
  ): Mono<ResponseEntity<Void>> =
    service.requireTenant(apiKey)
      .flatMap { service.deleteWebhook(it.id, id) }
      .thenReturn(ResponseEntity.noContent().build())

  @PostMapping("/webhooks/{id}/resume")
  fun resume(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @PathVariable id: String,
  ): Mono<WebhookResponse> =
    service.requireTenant(apiKey).flatMap { service.resume(it.id, id) }

  @PostMapping("/webhooks/{id}/rotate-secret")
  fun rotate(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @PathVariable id: String,
  ): Mono<RotateSecretResponse> =
    service.requireTenant(apiKey).flatMap { service.rotateSecret(it.id, id) }

  @PostMapping("/webhooks/{id}/replay-dlq")
  fun replayDlq(
    @RequestHeader("X-Api-Key") apiKey: String?,
    @PathVariable id: String,
  ): Mono<CountResponse> =
    service.requireTenant(apiKey).flatMap { service.replayDlq(it.id, id) }

  @PostMapping("/internal/events")
  fun accept(@RequestBody req: AcceptEventRequest): Mono<ResponseEntity<AcceptEventResponse>> =
    service.acceptEvent(req).map { (body, status) -> ResponseEntity.status(status).body(body) }

  @GetMapping("/deliveries")
  fun deliveries(@RequestParam eventId: String): Flux<DeliveryResponse> =
    service.listDeliveries(eventId)

  @PostMapping("/deliveries/{id}/replay")
  fun replayDelivery(@PathVariable id: String): Mono<CountResponse> =
    service.replayDelivery(id)
}
