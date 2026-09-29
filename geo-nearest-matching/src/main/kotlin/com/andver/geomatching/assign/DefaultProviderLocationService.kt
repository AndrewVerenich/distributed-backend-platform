package com.andver.geomatching.assign

import com.andver.geomatching.geo.GeoIndex
import com.andver.geomatching.metrics.GeoMatchingMetrics
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

interface ProviderLocationService {
  fun updateLocation(id: String, lng: Double, lat: Double): Mono<Provider>
  fun updateStatus(id: String, status: ProviderStatus): Mono<Provider>
}

@Component
class DefaultProviderLocationService(
  private val providers: ProviderStore,
  private val geoIndex: GeoIndex,
  private val metrics: GeoMatchingMetrics,
  private val clock: Clock = Clock.systemUTC(),
) : ProviderLocationService {

  override fun updateLocation(id: String, lng: Double, lat: Double): Mono<Provider> {
    val now = Instant.now(clock)
    return providers.upsertLocation(id, lng, lat, now)
      .flatMap { provider ->
        metrics.locationUpdate()
        syncIndex(provider)
      }
  }

  override fun updateStatus(id: String, status: ProviderStatus): Mono<Provider> =
    providers.setStatus(id, status)
      .flatMap { provider -> syncIndex(provider) }

  private fun syncIndex(provider: Provider): Mono<Provider> {
    val lng = provider.lng
    val lat = provider.lat
    val indexable = provider.status == ProviderStatus.FREE && lng != null && lat != null
    val op = if (indexable) {
      geoIndex.add(provider.id, lng!!, lat!!)
    } else {
      geoIndex.remove(provider.id)
    }
    return op.onErrorResume { Mono.empty() }.thenReturn(provider)
  }
}
