package com.andver.hash.router.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class RouterMetrics(
  private val registry: MeterRegistry,
) {
  private val activeNodes = AtomicInteger(0)
  private val routeCounters = ConcurrentHashMap<String, Counter>()
  private val routeTimers = ConcurrentHashMap<String, Timer>()
  private val nodesAdded = Counter.builder("chr_nodes_added_total").register(registry)
  private val nodesRemoved = Counter.builder("chr_nodes_removed_total").register(registry)

  init {
    registry.gauge("chr_nodes_active", activeNodes) { it.get().toDouble() }
  }

  fun recordRoute(nodeId: String, result: String, duration: Duration) {
    routeCounters.computeIfAbsent("$nodeId/$result") {
      Counter.builder("chr_route_total")
        .tag("node", nodeId)
        .tag("result", result)
        .register(registry)
    }.increment()
    if (result == "ok" || result == "bad_gateway") {
      routeTimers.computeIfAbsent(nodeId) {
        Timer.builder("chr_route_seconds")
          .tag("node", nodeId)
          .publishPercentiles(0.5, 0.95, 0.99)
          .register(registry)
      }.record(duration)
    }
  }

  fun recordNoNodes() {
    routeCounters.computeIfAbsent("none/no_nodes") {
      Counter.builder("chr_route_total")
        .tag("node", "none")
        .tag("result", "no_nodes")
        .register(registry)
    }.increment()
  }

  fun nodeAdded() {
    nodesAdded.increment()
    activeNodes.incrementAndGet()
  }

  fun nodeRemoved() {
    nodesRemoved.increment()
    activeNodes.decrementAndGet()
  }

  fun setActiveNodes(count: Int) {
    activeNodes.set(count)
  }
}
