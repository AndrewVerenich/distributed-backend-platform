package com.andver.geomatching.store

import com.andver.geomatching.model.GeoRequest
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface RequestStore {
  fun insertPending(request: GeoRequest): Mono<GeoRequest>
  fun findById(id: String): Mono<GeoRequest>
  fun markAssigned(id: String, providerId: String, distanceM: Double, at: Instant): Mono<GeoRequest>
  fun markNoProviders(id: String, at: Instant): Mono<GeoRequest>
  fun markCompleted(id: String, at: Instant): Mono<GeoRequest>
  fun markAbandoned(id: String, at: Instant): Mono<Long>
  fun findAssignedOlderThan(cutoff: Instant): Flux<GeoRequest>
}

@Component
class R2dbcRequestStore(
  private val requests: RequestRepository,
) : RequestStore {

  override fun insertPending(request: GeoRequest): Mono<GeoRequest> =
    requests.insertPending(
      request.id,
      request.lng,
      request.lat,
      request.radiusM,
      request.createdAt ?: Instant.now(),
    )

  override fun findById(id: String): Mono<GeoRequest> = requests.findById(id)

  override fun markAssigned(id: String, providerId: String, distanceM: Double, at: Instant): Mono<GeoRequest> =
    requests.markAssigned(id, providerId, distanceM, at)

  override fun markNoProviders(id: String, at: Instant): Mono<GeoRequest> =
    requests.markNoProviders(id, at)

  override fun markCompleted(id: String, at: Instant): Mono<GeoRequest> =
    requests.markCompleted(id, at)

  override fun markAbandoned(id: String, at: Instant): Mono<Long> =
    requests.markAbandonedReturningId(id, at).hasElement().map { if (it) 1L else 0L }

  override fun findAssignedOlderThan(cutoff: Instant): Flux<GeoRequest> =
    requests.findAssignedOlderThan(cutoff)
}
