package com.andver.geomatching.store

import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface ProviderStore {
  fun upsertLocation(id: String, lng: Double, lat: Double, at: Instant): Mono<Provider>
  fun findById(id: String): Mono<Provider>
  fun tryClaim(providerId: String, requestId: String, cutoff: Instant): Mono<Boolean>
  fun markFree(providerId: String, expectedRequestId: String?): Mono<Provider>
  fun setStatus(id: String, status: ProviderStatus): Mono<Provider>
  fun markStaleOffline(cutoff: Instant): Flux<Provider>
  fun findFreeFresh(cutoff: Instant): Flux<Provider>
  fun countFreeFresh(cutoff: Instant): Mono<Long>
}

@Component
class R2dbcProviderStore(
  private val providers: ProviderRepository,
) : ProviderStore {

  override fun upsertLocation(id: String, lng: Double, lat: Double, at: Instant): Mono<Provider> =
    providers.upsertLocation(id, lng, lat, at)

  override fun findById(id: String): Mono<Provider> = providers.findById(id)

  override fun tryClaim(providerId: String, requestId: String, cutoff: Instant): Mono<Boolean> =
    providers.tryClaim(providerId, requestId, cutoff).hasElement()

  override fun markFree(providerId: String, expectedRequestId: String?): Mono<Provider> =
    if (expectedRequestId == null) {
      providers.markFreeById(providerId)
    } else {
      providers.markFreeIfAssigned(providerId, expectedRequestId)
    }

  override fun setStatus(id: String, status: ProviderStatus): Mono<Provider> =
    if (status == ProviderStatus.FREE) {
      providers.setStatusClearRequest(id, status)
    } else {
      providers.setStatus(id, status)
    }

  override fun markStaleOffline(cutoff: Instant): Flux<Provider> =
    providers.markStaleOffline(cutoff)

  override fun findFreeFresh(cutoff: Instant): Flux<Provider> =
    providers.findFreeFresh(cutoff)

  override fun countFreeFresh(cutoff: Instant): Mono<Long> =
    providers.countFreeFresh(cutoff).defaultIfEmpty(0)
}
