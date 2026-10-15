package com.andver.circuitbreaker.metrics

import com.andver.circuitbreaker.core.CircuitBreakerState
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MicrometerCircuitBreakerMetrics(
  private val registry: MeterRegistry,
) : CircuitBreakerMetrics {
  private val states = ConcurrentHashMap<String, AtomicInteger>()
  private val failureRates = ConcurrentHashMap<String, AtomicReference<Float>>()
  private val slowRates = ConcurrentHashMap<String, AtomicReference<Float>>()

  override fun bind(command: String) {
    states.computeIfAbsent(command) { cmd ->
      val holder = AtomicInteger(CircuitBreakerState.CLOSED.metricValue())
      Gauge
        .builder("circuit.breaker.state", holder) { it.get().toDouble() }
        .tag("command", cmd)
        .description("0=CLOSED 1=OPEN 2=HALF_OPEN")
        .register(registry)
      holder
    }
    failureRates.computeIfAbsent(command) { cmd ->
      val holder = AtomicReference(0f)
      Gauge
        .builder("circuit.breaker.failure.rate", holder) { it.get().toDouble() }
        .tag("command", cmd)
        .description("Failure rate percent in the current sliding window")
        .register(registry)
      holder
    }
    slowRates.computeIfAbsent(command) { cmd ->
      val holder = AtomicReference(0f)
      Gauge
        .builder("circuit.breaker.slow.call.rate", holder) { it.get().toDouble() }
        .tag("command", cmd)
        .description("Slow-call rate percent in the current sliding window")
        .register(registry)
      holder
    }
  }

  override fun onState(
    command: String,
    state: CircuitBreakerState,
  ) {
    states[command]?.set(state.metricValue())
  }

  override fun onRates(
    command: String,
    failureRate: Float,
    slowCallRate: Float,
  ) {
    failureRates[command]?.set(failureRate)
    slowRates[command]?.set(slowCallRate)
  }

  override fun recordCall(
    command: String,
    outcome: String,
  ) {
    Counter
      .builder("circuit.breaker.calls")
      .tag("command", command)
      .tag("outcome", outcome)
      .register(registry)
      .increment()
  }
}
