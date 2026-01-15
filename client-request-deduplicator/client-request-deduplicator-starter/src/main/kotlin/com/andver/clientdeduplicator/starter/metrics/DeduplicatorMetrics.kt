package com.andver.clientdeduplicator.starter.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap

interface DeduplicatorMetrics {
  fun hit(method: String, url: String)
  fun miss(method: String, url: String)
  fun bypass(method: String, url: String)
}

private const val CLIENT_REQUEST_DEDUP_CACHE_TOTAL = "client_request_dedup_cache_total"
private const val CLIENT_REQUEST_DEDUP_CACHE_HIT = "client_request_dedup_cache_hit"
private const val CLIENT_REQUEST_DEDUP_CACHE_MISS = "client_request_dedup_cache_miss"
private const val CLIENT_REQUEST_DEDUP_CACHE_BYPASS = "client_request_dedup_cache_bypass"

class DefaultDeduplicatorMetrics(
  private val meterRegistry: MeterRegistry,
) : DeduplicatorMetrics {
  private val totalCounters = ConcurrentHashMap<String, Counter>()
  private val hitCounters = ConcurrentHashMap<String, Counter>()
  private val missCounters = ConcurrentHashMap<String, Counter>()
  private val bypassCounters = ConcurrentHashMap<String, Counter>()

  override fun hit(method: String, url: String) {
    counter(totalCounters, CLIENT_REQUEST_DEDUP_CACHE_TOTAL, method, url).increment()
    counter(hitCounters, CLIENT_REQUEST_DEDUP_CACHE_HIT, method, url).increment()
  }

  override fun miss(method: String, url: String) {
    counter(totalCounters, CLIENT_REQUEST_DEDUP_CACHE_TOTAL, method, url).increment()
    counter(missCounters, CLIENT_REQUEST_DEDUP_CACHE_MISS, method, url).increment()
  }

  override fun bypass(method: String, url: String) {
    counter(totalCounters, CLIENT_REQUEST_DEDUP_CACHE_TOTAL, method, url).increment()
    counter(bypassCounters, CLIENT_REQUEST_DEDUP_CACHE_BYPASS, method, url).increment()
  }

  private fun counter(
    map: ConcurrentHashMap<String, Counter>,
    name: String,
    method: String,
    url: String
  ): Counter {
    val key = "$method|$url"
    return map.getOrPut(key) {
      Counter.builder(name)
        .tag("method", method)
        .tag("url", url)
        .register(meterRegistry)
    }
  }
}