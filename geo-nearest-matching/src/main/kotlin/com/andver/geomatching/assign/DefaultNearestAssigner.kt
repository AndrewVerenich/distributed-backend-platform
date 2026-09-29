package com.andver.geomatching.assign

import com.andver.geomatching.GeoMatchingProperties
import com.andver.geomatching.geo.GeoIndex
import com.andver.geomatching.metrics.GeoMatchingMetrics
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.model.AssignResult
import com.andver.geomatching.model.GeoCandidate
import com.andver.geomatching.model.GeoRequest
import com.andver.geomatching.model.NoProviderReason
import com.andver.geomatching.model.RequestStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

interface NearestAssigner {
  fun assign(requestId: String?, lng: Double, lat: Double, radiusM: Double?): Mono<AssignResult>
}

@Component
class DefaultNearestAssigner(
  private val geoIndex: GeoIndex,
  private val providers: ProviderStore,
  private val requests: RequestStore,
  private val tx: TransactionalOperator,
  private val properties: GeoMatchingProperties,
  private val metrics: GeoMatchingMetrics,
  private val clock: Clock = Clock.systemUTC(),
) : NearestAssigner {

  override fun assign(requestId: String?, lng: Double, lat: Double, radiusM: Double?): Mono<AssignResult> {
    val started = Instant.now(clock)
    val id = requestId ?: UUID.randomUUID().toString()
    val radius = radiusM ?: properties.defaultRadiusM
    val now = Instant.now(clock)
    val pending = GeoRequest(
      id = id,
      status = RequestStatus.PENDING,
      lng = lng,
      lat = lat,
      radiusM = radius,
      createdAt = now,
      updatedAt = now,
    )
    val cutoff = now.minus(properties.heartbeatTtl)

    return requests.insertPending(pending)
      .then(geoIndex.search(lng, lat, radius, properties.candidateCount))
      .flatMap { candidates ->
        metrics.geoSearch(candidates.size)
        if (candidates.isEmpty()) {
          metrics.noCandidates()
          finish(started, "no_candidates")
          requests.markNoProviders(id, Instant.now(clock))
            .thenReturn(AssignResult.NoProviders(id, NoProviderReason.NO_CANDIDATES))
        } else {
          claimInOrder(id, candidates, cutoff, started)
        }
      }
  }

  private fun claimInOrder(
    requestId: String,
    candidates: List<GeoCandidate>,
    cutoff: Instant,
    started: Instant,
  ): Mono<AssignResult> =
    claimFirst(requestId, candidates, cutoff)
      .flatMap { assigned ->
        geoIndex.remove(assigned.providerId)
          .onErrorResume { Mono.empty() }
          .then(Mono.fromCallable {
            metrics.assignSuccess()
            finish(started, "assigned")
            assigned as AssignResult
          })
      }
      .switchIfEmpty(
        Mono.defer {
          metrics.candidateListExhausted()
          finish(started, "exhausted")
          requests.markNoProviders(requestId, Instant.now(clock))
            .thenReturn(AssignResult.NoProviders(requestId, NoProviderReason.CANDIDATE_LIST_EXHAUSTED) as AssignResult)
        }
      )

  private fun claimFirst(
    requestId: String,
    candidates: List<GeoCandidate>,
    cutoff: Instant,
  ): Mono<AssignResult.Assigned> =
    Flux.fromIterable(candidates)
      .concatMap { candidate ->
        tx.transactional(
          providers.tryClaim(candidate.providerId, requestId, cutoff)
            .flatMap { claimed ->
              if (!claimed) {
                metrics.assignConflict()
                Mono.empty()
              } else {
                requests.markAssigned(
                  requestId,
                  candidate.providerId,
                  candidate.distanceM,
                  Instant.now(clock),
                ).map {
                  AssignResult.Assigned(requestId, candidate.providerId, candidate.distanceM)
                }
              }
            }
        )
      }
      .next()

  private fun finish(started: Instant, outcome: String) {
    metrics.recordAssign(Duration.between(started, Instant.now(clock)), outcome)
  }
}
