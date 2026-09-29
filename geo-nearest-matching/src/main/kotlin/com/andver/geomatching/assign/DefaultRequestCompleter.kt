package com.andver.geomatching.assign

import com.andver.geomatching.geo.GeoIndex
import com.andver.geomatching.model.CompleteResult
import com.andver.geomatching.store.ProviderStore
import com.andver.geomatching.store.RequestStore
import com.andver.geomatching.model.ProviderStatus
import com.andver.geomatching.model.RequestStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

interface RequestCompleter {
  fun complete(requestId: String): Mono<CompleteResult>
}

@Component
class DefaultRequestCompleter(
  private val requests: RequestStore,
  private val providers: ProviderStore,
  private val geoIndex: GeoIndex,
  private val tx: TransactionalOperator,
  private val clock: Clock = Clock.systemUTC(),
) : RequestCompleter {

  override fun complete(requestId: String): Mono<CompleteResult> {
    val now = Instant.now(clock)
    return tx.transactional(
      requests.markCompleted(requestId, now)
        .flatMap { request ->
          val providerId = request.providerId
            val release = if (providerId == null) {
            Mono.just(request)
          } else {
            providers.markFree(providerId, requestId)
              .thenReturn(request)
              .defaultIfEmpty(request)
          }
          release
        }
    ).flatMap { request ->
      val providerId = request.providerId
      val reindex = if (providerId == null) {
        Mono.empty()
      } else {
        providers.findById(providerId)
          .filter { it.status == ProviderStatus.FREE && it.lng != null && it.lat != null }
          .flatMap { geoIndex.add(it.id, it.lng!!, it.lat!!) }
          .onErrorResume { Mono.empty() }
      }
      reindex.thenReturn(
        CompleteResult(
          requestId = request.id,
          providerId = providerId,
          status = RequestStatus.COMPLETED,
        )
      )
    }
  }
}
