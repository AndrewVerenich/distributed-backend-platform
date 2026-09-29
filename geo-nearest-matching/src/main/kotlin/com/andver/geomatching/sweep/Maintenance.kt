package com.andver.geomatching.sweep

import com.andver.geomatching.GeoMatchingProperties
import com.andver.geomatching.geo.GeoIndex
import com.andver.geomatching.metrics.GeoMatchingMetrics
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.model.IndexStats
import com.andver.geomatching.model.ReconcileResult
import com.andver.geomatching.model.SweepResult
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import kotlin.math.abs

interface StaleSweeper {
  fun sweep(): Mono<SweepResult>
}

interface IndexStatsService {
  fun stats(): Mono<IndexStats>
}

interface IndexReconciler : IndexStatsService {
  fun reconcile(): Mono<ReconcileResult>
}

@Component
class DefaultStaleSweeper(
  private val providers: ProviderStore,
  private val requests: RequestStore,
  private val geoIndex: GeoIndex,
  private val properties: GeoMatchingProperties,
  private val metrics: GeoMatchingMetrics,
  private val clock: Clock = Clock.systemUTC(),
) : StaleSweeper {
  override fun sweep(): Mono<SweepResult> {
    val now = Instant.now(clock)
    val cutoff = now.minus(properties.heartbeatTtl)
    return providers.markStaleOffline(cutoff)
      .concatMap { provider ->
        geoIndex.remove(provider.id).onErrorResume { Mono.empty() }
          .then(
            if (provider.currentRequestId == null) {
              Mono.just(0L)
            } else {
              requests.markAbandoned(provider.currentRequestId, now)
            }
          )
      }
      .reduce(SweepResult(0, 0)) { acc, abandoned ->
        SweepResult(
          evictedProviders = acc.evictedProviders + 1,
          abandonedRequests = acc.abandonedRequests + if (abandoned > 0) 1 else 0,
        )
      }
      .defaultIfEmpty(SweepResult(0, 0))
      .doOnNext { metrics.staleEvictions(it.evictedProviders) }
  }
}

@Component
class DefaultIndexReconciler(
  private val providers: ProviderStore,
  private val geoIndex: GeoIndex,
  private val properties: GeoMatchingProperties,
  private val metrics: GeoMatchingMetrics,
  private val clock: Clock = Clock.systemUTC(),
) : IndexReconciler {

  override fun reconcile(): Mono<ReconcileResult> {
    val cutoff = Instant.now(clock).minus(properties.heartbeatTtl)
    return Mono.zip(
      providers.findFreeFresh(cutoff).collectList(),
      geoIndex.members().collectList(),
    ).flatMap { tuple ->
      val free = tuple.t1
      val indexed = tuple.t2.toSet()
      val freeIds = free.map { it.id }.toSet()
      val toAdd = free.filter { it.id !in indexed && it.lng != null && it.lat != null }
      val toRemove = indexed.filter { it !in freeIds }

      val addOps = Flux.fromIterable(toAdd)
        .concatMap { geoIndex.add(it.id, it.lng!!, it.lat!!).onErrorResume { Mono.empty() } }
        .then()
      val removeOps = Flux.fromIterable(toRemove)
        .concatMap { geoIndex.remove(it).onErrorResume { Mono.empty() } }
        .then()

      addOps.then(removeOps).thenReturn(
        ReconcileResult(added = toAdd.size, removed = toRemove.size)
      )
    }.doOnNext { result ->
      metrics.indexReconcile(result.added, result.removed)
    }.flatMap { result ->
      refreshStats().thenReturn(result)
    }
  }

  override fun stats(): Mono<IndexStats> = refreshStats()

  private fun refreshStats(): Mono<IndexStats> {
    val cutoff = Instant.now(clock).minus(properties.heartbeatTtl)
    return Mono.zip(
      geoIndex.size(),
      providers.countFreeFresh(cutoff),
    ).map { tuple ->
      val redis = tuple.t1
      val pg = tuple.t2
      val drift = abs(redis - pg)
      metrics.setIndexSize(redis)
      metrics.setPgFreeFresh(pg)
      metrics.setDrift(drift)
      IndexStats(redisMembers = redis, pgFreeFresh = pg, drift = drift)
    }
  }
}
