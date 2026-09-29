package com.andver.geomatching.support

import com.andver.geomatching.geo.GeoIndex
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.model.GeoCandidate
import com.andver.geomatching.model.GeoRequest
import com.andver.geomatching.model.Provider
import com.andver.geomatching.model.ProviderStatus
import com.andver.geomatching.model.RequestStatus
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.reactive.TransactionCallback
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class PassthroughTransactionalOperator : TransactionalOperator {
  override fun <T : Any?> execute(action: TransactionCallback<T>): Flux<T> {
    return Flux.from(
      action.doInTransaction(
        object : ReactiveTransaction {
          override fun isNewTransaction() = true
          override fun setRollbackOnly() {}
          override fun isRollbackOnly() = false
          override fun isCompleted() = false
        }
      )
    )
  }
}

class InMemoryGeoIndex : GeoIndex {
  private val coords = ConcurrentHashMap<String, Pair<Double, Double>>()

  override fun add(providerId: String, lng: Double, lat: Double): Mono<Void> {
    coords[providerId] = lng to lat
    return Mono.empty()
  }

  override fun remove(providerId: String): Mono<Void> {
    coords.remove(providerId)
    return Mono.empty()
  }

  override fun search(lng: Double, lat: Double, radiusM: Double, count: Long): Mono<List<GeoCandidate>> {
    val ranked = coords.mapNotNull { (id, point) ->
      val d = haversineMeters(lng, lat, point.first, point.second)
      if (d <= radiusM) GeoCandidate(id, d) else null
    }.sortedBy { it.distanceM }.take(count.toInt())
    return Mono.just(ranked)
  }

  override fun members(): Flux<String> = Flux.fromIterable(coords.keys)

  override fun size(): Mono<Long> = Mono.just(coords.size.toLong())

  fun contains(id: String): Boolean = coords.containsKey(id)
}

class FakeProviderStore : ProviderStore {
  private val rows = ConcurrentHashMap<String, Provider>()

  override fun upsertLocation(id: String, lng: Double, lat: Double, at: Instant): Mono<Provider> {
    val updated = rows.compute(id) { _, current ->
      if (current == null) {
        Provider(id, ProviderStatus.FREE, lng, lat, at, null)
      } else {
        val status = if (current.status == ProviderStatus.OFFLINE) ProviderStatus.FREE else current.status
        current.copy(status = status, lng = lng, lat = lat, lastSeenAt = at)
      }
    }!!
    return Mono.just(updated)
  }

  override fun findById(id: String): Mono<Provider> = Mono.justOrEmpty(rows[id])

  override fun tryClaim(providerId: String, requestId: String, cutoff: Instant): Mono<Boolean> {
    var claimed = false
    rows.computeIfPresent(providerId) { _, current ->
      val fresh = current.lastSeenAt != null && current.lastSeenAt!! > cutoff
      if (current.status == ProviderStatus.FREE && fresh) {
        claimed = true
        current.copy(status = ProviderStatus.BUSY, currentRequestId = requestId)
      } else current
    }
    return Mono.just(claimed)
  }

  override fun markFree(providerId: String, expectedRequestId: String?): Mono<Provider> {
    var result: Provider? = null
    rows.computeIfPresent(providerId) { _, current ->
      if (expectedRequestId != null && current.currentRequestId != expectedRequestId) {
        current
      } else {
        val updated = current.copy(status = ProviderStatus.FREE, currentRequestId = null)
        result = updated
        updated
      }
    }
    return Mono.justOrEmpty(result)
  }

  override fun setStatus(id: String, status: ProviderStatus): Mono<Provider> {
    val updated = rows.computeIfPresent(id) { _, current ->
      if (status == ProviderStatus.FREE) current.copy(status = status, currentRequestId = null)
      else current.copy(status = status)
    }
    return Mono.justOrEmpty(updated)
  }

  override fun markStaleOffline(cutoff: Instant): Flux<Provider> {
    val stale = rows.values.filter {
      it.status != ProviderStatus.OFFLINE && it.lastSeenAt != null && it.lastSeenAt!! < cutoff
    }
    val snapshot = stale.map { provider ->
      val updated = provider.copy(status = ProviderStatus.OFFLINE, currentRequestId = null)
      rows[provider.id] = updated
      updated.copy(currentRequestId = provider.currentRequestId)
    }
    return Flux.fromIterable(snapshot)
  }

  override fun findFreeFresh(cutoff: Instant): Flux<Provider> =
    Flux.fromIterable(
      rows.values.filter {
        it.status == ProviderStatus.FREE &&
          it.lastSeenAt != null && it.lastSeenAt!! > cutoff &&
          it.lng != null && it.lat != null
      }
    )

  override fun countFreeFresh(cutoff: Instant): Mono<Long> =
    findFreeFresh(cutoff).count()

  fun put(provider: Provider) {
    rows[provider.id] = provider
  }

  fun get(id: String): Provider? = rows[id]
}

class FakeRequestStore : RequestStore {
  private val rows = ConcurrentHashMap<String, GeoRequest>()

  override fun insertPending(request: GeoRequest): Mono<GeoRequest> {
    rows[request.id] = request
    return Mono.just(request)
  }

  override fun findById(id: String): Mono<GeoRequest> = Mono.justOrEmpty(rows[id])

  override fun markAssigned(id: String, providerId: String, distanceM: Double, at: Instant): Mono<GeoRequest> {
    val updated = rows.computeIfPresent(id) { _, current ->
      if (current.status == RequestStatus.PENDING) {
        current.copy(status = RequestStatus.ASSIGNED, providerId = providerId, distanceM = distanceM, updatedAt = at)
      } else current
    }
    return if (updated?.status == RequestStatus.ASSIGNED) Mono.just(updated) else Mono.empty()
  }

  override fun markNoProviders(id: String, at: Instant): Mono<GeoRequest> {
    val updated = rows.computeIfPresent(id) { _, current ->
      if (current.status == RequestStatus.PENDING) {
        current.copy(status = RequestStatus.NO_PROVIDERS, updatedAt = at)
      } else current
    }
    return Mono.justOrEmpty(updated)
  }

  override fun markCompleted(id: String, at: Instant): Mono<GeoRequest> {
    val updated = rows.computeIfPresent(id) { _, current ->
      if (current.status == RequestStatus.ASSIGNED) {
        current.copy(status = RequestStatus.COMPLETED, updatedAt = at)
      } else current
    }
    return if (updated?.status == RequestStatus.COMPLETED) Mono.just(updated) else Mono.empty()
  }

  override fun markAbandoned(id: String, at: Instant): Mono<Long> {
    var changed = 0L
    rows.computeIfPresent(id) { _, current ->
      if (current.status == RequestStatus.ASSIGNED) {
        changed = 1
        current.copy(status = RequestStatus.NO_PROVIDERS, updatedAt = at)
      } else current
    }
    return Mono.just(changed)
  }

  override fun findAssignedOlderThan(cutoff: Instant): Flux<GeoRequest> =
    Flux.fromIterable(
      rows.values.filter { it.status == RequestStatus.ASSIGNED && (it.updatedAt ?: Instant.MAX) < cutoff }
    )

  fun get(id: String): GeoRequest? = rows[id]
}

internal fun haversineMeters(lng1: Double, lat1: Double, lng2: Double, lat2: Double): Double {
  val earth = 6_371_000.0
  val dLat = Math.toRadians(lat2 - lat1)
  val dLng = Math.toRadians(lng2 - lng1)
  val a = sin(dLat / 2).pow(2) +
    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
  return 2 * earth * asin(min(1.0, sqrt(a)))
}
