package com.andver.banking.query.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

interface QueryMetrics {
  fun recordBalanceQuery(duration: Duration)
  fun recordTemporalQuery(path: String, duration: Duration, eventsReplayed: Long)

  companion object {
    val NOOP: QueryMetrics = object : QueryMetrics {
      override fun recordBalanceQuery(duration: Duration) = Unit
      override fun recordTemporalQuery(path: String, duration: Duration, eventsReplayed: Long) = Unit
    }
  }
}

@Component
class MicrometerQueryMetrics(
  private val registry: MeterRegistry,
) : QueryMetrics {

  private val balanceTimer = Timer.builder("es_query_balance_seconds")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)

  private val balanceTotal = Counter.builder("es_query_balance_total").register(registry)

  private val temporalTimers = ConcurrentHashMap<String, Timer>()
  private val temporalTotals = ConcurrentHashMap<String, Counter>()
  private val eventsReplayed = ConcurrentHashMap<String, DistributionSummary>()

  override fun recordBalanceQuery(duration: Duration) {
    balanceTotal.increment()
    balanceTimer.record(duration)
  }

  override fun recordTemporalQuery(path: String, duration: Duration, eventsReplayed: Long) {
    temporalTotals.computeIfAbsent(path) {
      Counter.builder("es_query_temporal_total")
        .tag("path", path)
        .register(registry)
    }.increment()
    temporalTimers.computeIfAbsent(path) {
      Timer.builder("es_query_temporal_seconds")
        .tag("path", path)
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
    }.record(duration)
    this.eventsReplayed.computeIfAbsent(path) {
      DistributionSummary.builder("es_query_temporal_events_replayed")
        .tag("path", path)
        .register(registry)
    }.record(eventsReplayed.toDouble())
  }
}
