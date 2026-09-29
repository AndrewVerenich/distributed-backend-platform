package com.andver.geomatching.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

interface GeoMatchingMetrics {
  fun locationUpdate()
  fun geoSearch(candidateCount: Int)
  fun assignSuccess()
  fun assignConflict()
  fun noCandidates()
  fun candidateListExhausted()
  fun recordAssign(duration: Duration, outcome: String)
  fun staleEvictions(count: Int)
  fun indexReconcile(added: Int, removed: Int)
  fun setIndexSize(size: Long)
  fun setPgFreeFresh(count: Long)
  fun setDrift(drift: Long)
}

@Component
class MicrometerGeoMatchingMetrics(
  private val registry: MeterRegistry,
) : GeoMatchingMetrics {

  private val indexSize = AtomicLong(0)
  private val pgFreeFresh = AtomicLong(0)
  private val drift = AtomicLong(0)

  init {
    registry.gauge("geo_index_size", indexSize)
    registry.gauge("geo_pg_free_fresh", pgFreeFresh)
    registry.gauge("geo_index_drift", drift)
  }

  override fun locationUpdate() {
    registry.counter("geo_location_updates").increment()
  }

  override fun geoSearch(candidateCount: Int) {
    registry.counter("geo_search").increment()
    registry.summary("geo_search_candidates").record(candidateCount.toDouble())
  }

  override fun assignSuccess() {
    registry.counter("geo_assign_success").increment()
  }

  override fun assignConflict() {
    registry.counter("geo_assign_conflict").increment()
  }

  override fun noCandidates() {
    registry.counter("geo_no_candidates").increment()
  }

  override fun candidateListExhausted() {
    registry.counter("geo_candidate_list_exhausted").increment()
  }

  override fun recordAssign(duration: Duration, outcome: String) {
    Timer.builder("geo_assign")
      .tag("outcome", outcome)
      .publishPercentileHistogram()
      .register(registry)
      .record(duration)
  }

  override fun staleEvictions(count: Int) {
    if (count > 0) {
      registry.counter("geo_stale_evictions").increment(count.toDouble())
    }
  }

  override fun indexReconcile(added: Int, removed: Int) {
    if (added > 0) {
      registry.counter("geo_index_reconcile", "op", "add").increment(added.toDouble())
    }
    if (removed > 0) {
      registry.counter("geo_index_reconcile", "op", "remove").increment(removed.toDouble())
    }
  }

  override fun setIndexSize(size: Long) {
    indexSize.set(size)
  }

  override fun setPgFreeFresh(count: Long) {
    pgFreeFresh.set(count)
  }

  override fun setDrift(drift: Long) {
    this.drift.set(drift)
  }
}
