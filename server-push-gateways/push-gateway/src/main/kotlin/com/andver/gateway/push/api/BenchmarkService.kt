package com.andver.gateway.push.api

import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.gateway.push.service.ConnectionGate
import com.andver.gateway.push.service.EventFanoutService
import com.andver.gateway.push.service.PendingPollRegistry
import com.andver.gateway.push.service.SseConnectionManager
import com.andver.push.model.PushEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

enum class BenchmarkTransport { SSE, POLL, BOTH }

data class BenchmarkRequest(
  val transport: BenchmarkTransport = BenchmarkTransport.SSE,
  val clients: Int = 100,
  val eventsPerClient: Int = 10,
  val rampUpMs: Long = 100,
)

data class BenchmarkComparisonRequest(
  val clients: Int = 100,
  val eventsPerClient: Int = 5,
)

data class BenchmarkResult(
  val transport: String,
  val clients: Int,
  val eventsPerClient: Int,
  val eventsReceived: Int,
  val p50LatencyMs: Double,
  val p99LatencyMs: Double,
  val elapsedMs: Long,
)

data class BenchmarkComparisonResult(
  val clients: Int,
  val eventsPerClient: Int,
  val results: List<BenchmarkResult>,
)

interface BenchmarkService {
  fun run(request: BenchmarkRequest): Mono<BenchmarkResult>
  fun compare(request: BenchmarkComparisonRequest): Mono<BenchmarkComparisonResult>
}

@Service
class DefaultBenchmarkService(
  private val connectionGate: ConnectionGate,
  private val sseConnectionManager: SseConnectionManager,
  private val pendingPollRegistry: PendingPollRegistry,
  private val eventFanoutService: EventFanoutService,
  private val metrics: DeliveryMetrics,
) : BenchmarkService {

  override fun run(request: BenchmarkRequest): Mono<BenchmarkResult> {
    require(request.clients in 1..500) {
      "in-process benchmark supports 1..500 clients; use Gatling scripts for 1000/5000"
    }
    require(request.eventsPerClient in 1..100) { "eventsPerClient must be 1..100" }
    connectionGate.assertAcceptable()

    return when (request.transport) {
      BenchmarkTransport.SSE -> runSse(request)
      BenchmarkTransport.POLL -> runPoll(request)
      BenchmarkTransport.BOTH -> runSse(request)
    }
  }

  override fun compare(request: BenchmarkComparisonRequest): Mono<BenchmarkComparisonResult> {
    val base = BenchmarkRequest(
      clients = request.clients.coerceIn(1, 500),
      eventsPerClient = request.eventsPerClient.coerceIn(1, 100),
    )
    return runSse(base)
      .flatMap { sse -> runPoll(base).map { poll -> sse to poll } }
      .map { (sse, poll) ->
        BenchmarkComparisonResult(
          clients = base.clients,
          eventsPerClient = base.eventsPerClient,
          results = listOf(sse, poll),
        )
      }
  }

  private fun runSse(request: BenchmarkRequest): Mono<BenchmarkResult> {
    val started = Instant.now()
    val latencies = ConcurrentLinkedQueue<Long>()
    val received = AtomicInteger(0)
    val clientIds = (1..request.clients).map { "bench-sse-$it-${System.nanoTime()}" }

    val sessions = clientIds.map { clientId ->
      eventFanoutService.ensureSubscribed(clientId)
      val session = sseConnectionManager.register(clientId)
      val disposable = session.sink.asFlux().subscribe { event ->
        received.incrementAndGet()
        val latency = Duration.between(event.publishedAt, Instant.now()).toMillis()
        if (latency >= 0) latencies.add(latency)
      }
      session to disposable
    }

    return Flux.range(0, request.clients)
      .delayElements(Duration.ofMillis(maxOf(0L, request.rampUpMs / request.clients)))
      .concatMap { clientIndex ->
        val clientId = clientIds[clientIndex]
        Flux.range(1, request.eventsPerClient)
          .concatMap { idx ->
            Mono.fromCallable {
              val event = PushEvent(
                eventId = (clientIndex + 1L) * 1_000 + idx,
                clientId = clientId,
                type = "benchmark.sse",
                payload = mapOf("i" to idx),
                publishedAt = Instant.now(),
              )
              sseConnectionManager.deliver(clientId, event)
            }.then(Mono.delay(Duration.ofMillis(1))).then()
          }
      }
      .then(Mono.delay(Duration.ofMillis(200)))
      .map {
        sessions.forEach { (session, disposable) ->
          disposable.dispose()
          sseConnectionManager.unregister(session)
          eventFanoutService.maybeUnsubscribe(session.clientId)
        }
        val result = toResult("sse", request, received.get(), latencies.toList(), started)
        metrics.recordBenchmark("sse", result.p50LatencyMs, result.p99LatencyMs)
        result
      }
  }

  private fun runPoll(request: BenchmarkRequest): Mono<BenchmarkResult> {
    val started = Instant.now()
    val latencies = ConcurrentLinkedQueue<Long>()
    val received = AtomicInteger(0)
    val clientIds = (1..request.clients).map { "bench-poll-$it-${System.nanoTime()}" }

    val waiters = clientIds.map { clientId ->
      eventFanoutService.ensureSubscribed(clientId)
      pendingPollRegistry.registerMono(clientId)
        .doOnNext { events ->
          events.forEach { event ->
            received.incrementAndGet()
            val latency = Duration.between(event.publishedAt, Instant.now()).toMillis()
            if (latency >= 0) latencies.add(latency)
          }
        }
        .onErrorResume { Mono.empty() }
    }

    val waitAll = Flux.merge(waiters).then()

    val publish = Flux.range(0, request.clients)
      .delayElements(Duration.ofMillis(maxOf(0L, request.rampUpMs / request.clients)))
      .concatMap { clientIndex ->
        val clientId = clientIds[clientIndex]
        Mono.delay(Duration.ofMillis(5)).then(
          Mono.fromCallable {
            val event = PushEvent(
              eventId = (clientIndex + 1L) * 1_000,
              clientId = clientId,
              type = "benchmark.poll",
              payload = mapOf("i" to 1),
              publishedAt = Instant.now(),
            )
            pendingPollRegistry.completeLocalWaiters(clientId, event)
          },
        )
      }
      .then()

    return Mono.`when`(waitAll, publish)
      .then(Mono.delay(Duration.ofMillis(100)))
      .map {
        clientIds.forEach { eventFanoutService.maybeUnsubscribe(it) }
        // poll benchmark delivers one event per client (waiter completes once)
        val result = toResult(
          "poll",
          request.copy(eventsPerClient = 1),
          received.get(),
          latencies.toList(),
          started,
        )
        metrics.recordBenchmark("poll", result.p50LatencyMs, result.p99LatencyMs)
        result
      }
  }

  private fun toResult(
    transport: String,
    request: BenchmarkRequest,
    eventsReceived: Int,
    latencies: List<Long>,
    started: Instant,
  ): BenchmarkResult {
    val sorted = latencies.sorted()
    return BenchmarkResult(
      transport = transport,
      clients = request.clients,
      eventsPerClient = request.eventsPerClient,
      eventsReceived = eventsReceived,
      p50LatencyMs = percentile(sorted, 0.50),
      p99LatencyMs = percentile(sorted, 0.99),
      elapsedMs = Duration.between(started, Instant.now()).toMillis(),
    )
  }

  private fun percentile(sorted: List<Long>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val index = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size) - 1
    return sorted[index].toDouble()
  }
}
