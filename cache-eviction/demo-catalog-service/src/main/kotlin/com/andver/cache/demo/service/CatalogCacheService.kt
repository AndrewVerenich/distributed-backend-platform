package com.andver.cache.demo.service

import com.andver.cache.api.BoundedCache
import com.andver.cache.api.BoundedCacheFactory
import com.andver.cache.api.BoundedCacheRegistry
import com.andver.cache.api.EvictionPolicyType
import com.andver.cache.autoconfigure.CacheConfigBuilder
import com.andver.cache.demo.api.BenchmarkComparisonRequest
import com.andver.cache.demo.api.BenchmarkComparisonResult
import com.andver.cache.demo.api.BenchmarkRequest
import com.andver.cache.demo.api.BenchmarkResult
import com.andver.cache.demo.model.CatalogItem
import com.andver.cache.demo.repo.SlowCatalogRepository
import com.andver.cache.demo.workload.LoopingWorkloadGenerator
import com.andver.cache.demo.workload.ScanWorkloadGenerator
import com.andver.cache.demo.workload.WorkloadGenerator
import com.andver.cache.demo.workload.WorkloadType
import com.andver.cache.demo.workload.ZipfWorkloadGenerator
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service
class CatalogCacheService(
  private val repository: SlowCatalogRepository,
  private val meterRegistry: MeterRegistry,
  private val cacheConfigBuilder: CacheConfigBuilder,
  private val cacheRegistry: BoundedCacheRegistry,
) {
  @Volatile
  private var cache: BoundedCache<String, CatalogItem> = defaultCache()

  private val benchmarkHitRatio = ConcurrentHashMap<String, AtomicReference<Double>>()
  private val benchmarkP99Micros = ConcurrentHashMap<String, AtomicReference<Double>>()
  private val benchmarkLoadCount = ConcurrentHashMap<String, AtomicReference<Double>>()
  private val benchmarkEvictions = ConcurrentHashMap<String, AtomicReference<Double>>()

  init {
    registerLiveCacheGauges()
  }

  fun getById(id: String): CatalogItem? = cache.getOrLoad(id) { repository.findById(it) }

  fun currentStats() = cache.stats()

  fun benchmark(req: BenchmarkRequest): BenchmarkResult = runBenchmark(req, replaceLiveCache = true)

  fun compare(req: BenchmarkComparisonRequest): BenchmarkComparisonResult {
    val results = req.policies.distinct().map { policy ->
      runBenchmark(req.toBenchmarkRequest(policy), replaceLiveCache = false)
    }
    return BenchmarkComparisonResult(
      workload = req.workload,
      requests = req.requests,
      results = results,
      bestByHitRatio = results.maxByOrNull { it.stats.hitRatio }?.policy,
      bestByP99Latency = results.minByOrNull { it.p99LatencyMicros }?.policy,
    )
  }

  private fun runBenchmark(req: BenchmarkRequest, replaceLiveCache: Boolean): BenchmarkResult {
    val benchmarkCache = newCache(
      policy = req.policy,
      maxSize = req.maxSize,
      ttl = Duration.ofSeconds(req.ttlSeconds),
      ttlJitter = Duration.ofSeconds(req.ttlJitterSeconds),
      singleflightEnabled = req.singleflightEnabled,
      negativeCachingEnabled = req.negativeCachingEnabled,
      bloomFilterEnabled = req.bloomFilterEnabled,
    )
    if (replaceLiveCache) {
      cache = benchmarkCache
    }

    val generator = workload(req)
    val latenciesMicros = LongArray(req.requests)
    val latencyTimer = Timer.builder("cache_demo_request_latency")
      .description("Latency for benchmarked cache requests")
      .tag("policy", req.policy.name)
      .tag("workload", req.workload.name)
      .register(meterRegistry)
    val started = System.currentTimeMillis()

    repeat(req.requests) { idx ->
      val key = generator.nextKey()
      val t0 = System.nanoTime()
      benchmarkCache.getOrLoad(key) { repository.findById(it) }
      val elapsedNanos = System.nanoTime() - t0
      latenciesMicros[idx] = elapsedNanos / 1_000
      latencyTimer.record(elapsedNanos, TimeUnit.NANOSECONDS)
    }

    latenciesMicros.sort()
    val elapsed = System.currentTimeMillis() - started
    val result = BenchmarkResult(
      policy = req.policy,
      workload = req.workload,
      requests = req.requests,
      elapsedMs = elapsed,
      p50LatencyMicros = percentile(latenciesMicros, 0.50),
      p99LatencyMicros = percentile(latenciesMicros, 0.99),
      stats = benchmarkCache.stats(),
    )
    meterRegistry.counter(
      "cache_benchmark_runs_total",
      "policy", req.policy.name,
      "workload", req.workload.name,
    ).increment()
    recordBenchmarkGauges(result)
    return result
  }

  private fun defaultCache(): BoundedCache<String, CatalogItem> =
    cacheRegistry.get(name = LIVE_CACHE_NAME, seedKeys = repository.knownKeys())

  private fun newCache(
    policy: EvictionPolicyType,
    maxSize: Int,
    ttl: Duration,
    ttlJitter: Duration,
    singleflightEnabled: Boolean,
    negativeCachingEnabled: Boolean,
    bloomFilterEnabled: Boolean,
  ): BoundedCache<String, CatalogItem> {
    return BoundedCacheFactory.create(
      cacheConfigBuilder.build(LIVE_CACHE_NAME, seedKeys = repository.knownKeys()).copy(
        maxSize = maxSize,
        policyType = policy,
        ttl = ttl,
        ttlJitter = ttlJitter,
        singleflightEnabled = singleflightEnabled,
        negativeCachingEnabled = negativeCachingEnabled,
        bloomFilterEnabled = bloomFilterEnabled,
      ),
    )
  }

  private fun workload(req: BenchmarkRequest): WorkloadGenerator = when (req.workload) {
    WorkloadType.ZIPF -> ZipfWorkloadGenerator(keyspace = req.keyspace, skew = req.zipfSkew)
    WorkloadType.SCAN -> ScanWorkloadGenerator(keyspace = req.keyspace)
    WorkloadType.LOOPING -> LoopingWorkloadGenerator(workingSet = req.keyspace)
  }

  private fun percentile(values: LongArray, p: Double): Long {
    if (values.isEmpty()) return 0L
    val index = ((values.size - 1) * p).toInt()
    return values[index]
  }

  private fun registerLiveCacheGauges() {
    Gauge.builder("cache_demo_live_hits_total") { currentStats().hits.toDouble() }
      .description("Total live cache hits")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_misses_total") { currentStats().misses.toDouble() }
      .description("Total live cache misses")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_evictions_total") { currentStats().evictions.toDouble() }
      .description("Total live cache evictions")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_load_total") { currentStats().loadCount.toDouble() }
      .description("Total live cache load operations")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_negative_hits_total") { currentStats().negativeHits.toDouble() }
      .description("Total live negative-cache hits")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_bloom_rejects_total") { currentStats().bloomRejects.toDouble() }
      .description("Total bloom filter rejections")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_size") { currentStats().size.toDouble() }
      .description("Current live cache size")
      .register(meterRegistry)
    Gauge.builder("cache_demo_live_hit_ratio") { currentStats().hitRatio }
      .description("Current live cache hit ratio")
      .register(meterRegistry)
  }

  private fun recordBenchmarkGauges(result: BenchmarkResult) {
    val key = "${result.policy.name}:${result.workload.name}"
    upsertGauge(
      metricName = "cache_benchmark_last_hit_ratio",
      storage = benchmarkHitRatio,
      key = key,
      tags = arrayOf("policy", result.policy.name, "workload", result.workload.name),
      value = result.stats.hitRatio,
      description = "Last benchmark hit ratio per policy/workload",
    )
    upsertGauge(
      metricName = "cache_benchmark_last_p99_micros",
      storage = benchmarkP99Micros,
      key = key,
      tags = arrayOf("policy", result.policy.name, "workload", result.workload.name),
      value = result.p99LatencyMicros.toDouble(),
      description = "Last benchmark p99 latency in microseconds",
    )
    upsertGauge(
      metricName = "cache_benchmark_last_load_count",
      storage = benchmarkLoadCount,
      key = key,
      tags = arrayOf("policy", result.policy.name, "workload", result.workload.name),
      value = result.stats.loadCount.toDouble(),
      description = "Last benchmark loader invocations",
    )
    upsertGauge(
      metricName = "cache_benchmark_last_evictions",
      storage = benchmarkEvictions,
      key = key,
      tags = arrayOf("policy", result.policy.name, "workload", result.workload.name),
      value = result.stats.evictions.toDouble(),
      description = "Last benchmark evictions",
    )
  }

  private fun upsertGauge(
    metricName: String,
    storage: ConcurrentHashMap<String, AtomicReference<Double>>,
    key: String,
    tags: Array<String>,
    value: Double,
    description: String,
  ) {
    val ref = storage.computeIfAbsent(key) {
      val created = AtomicReference(0.0)
      Gauge.builder(metricName, created) { it.get() }
        .description(description)
        .tags(*tags)
        .register(meterRegistry)
      created
    }
    ref.set(value)
  }

  companion object {
    const val LIVE_CACHE_NAME = "products"
  }
}
