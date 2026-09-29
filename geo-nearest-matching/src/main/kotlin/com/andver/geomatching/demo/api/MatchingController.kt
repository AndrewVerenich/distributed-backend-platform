package com.andver.geomatching.demo.api

import com.andver.geomatching.assign.NearestAssigner
import com.andver.geomatching.assign.ProviderLocationService
import com.andver.geomatching.assign.RequestCompleter
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.sweep.IndexStatsService
import com.andver.geomatching.model.AssignResult
import com.andver.geomatching.model.ProviderStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
class MatchingController(
  private val locations: ProviderLocationService,
  private val providers: ProviderStore,
  private val assigner: NearestAssigner,
  private val requests: RequestStore,
  private val completer: RequestCompleter,
  private val stats: IndexStatsService,
) {

  @PutMapping("/providers/{id}/location")
  fun location(@PathVariable id: String, @RequestBody body: LocationRequest) =
    locations.updateLocation(id, body.lng, body.lat)

  @PutMapping("/providers/{id}/status")
  fun status(@PathVariable id: String, @RequestBody body: StatusRequest) =
    locations.updateStatus(id, ProviderStatus.fromDb(body.status))

  @GetMapping("/providers/{id}")
  fun getProvider(@PathVariable id: String) =
    providers.findById(id)
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))

  @PostMapping("/requests")
  fun create(@RequestBody body: CreateRequestBody): Mono<AssignResponse> =
    assigner.assign(body.id, body.lng, body.lat, body.radiusM)
      .map { it.toResponse() }

  @GetMapping("/requests/{id}")
  fun getRequest(@PathVariable id: String) =
    requests.findById(id)
      .switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND)))

  @PostMapping("/requests/{id}/complete")
  fun complete(@PathVariable id: String): Mono<ResponseEntity<*>> =
    completer.complete(id)
      .map<ResponseEntity<*>> { ResponseEntity.ok(it) }
      .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("request is not assigned")))

  @GetMapping("/admin/stats")
  fun indexStats() = stats.stats()
}

private fun AssignResult.toResponse(): AssignResponse = when (this) {
  is AssignResult.Assigned -> AssignResponse(
    requestId = requestId,
    status = status.name,
    providerId = providerId,
    distanceM = distanceM,
  )
  is AssignResult.NoProviders -> AssignResponse(
    requestId = requestId,
    status = status.name,
    reason = reason.name,
  )
}
