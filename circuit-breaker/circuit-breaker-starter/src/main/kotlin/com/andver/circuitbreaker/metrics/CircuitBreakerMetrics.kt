package com.andver.circuitbreaker.metrics

import com.andver.circuitbreaker.core.CircuitBreakerState

interface CircuitBreakerMetrics {
  fun bind(command: String)

  fun onState(
    command: String,
    state: CircuitBreakerState,
  )

  fun onRates(
    command: String,
    failureRate: Float,
    slowCallRate: Float,
  )

  fun recordCall(
    command: String,
    outcome: String,
  )
}

object NoopCircuitBreakerMetrics : CircuitBreakerMetrics {
  override fun bind(command: String) = Unit

  override fun onState(
    command: String,
    state: CircuitBreakerState,
  ) = Unit

  override fun onRates(
    command: String,
    failureRate: Float,
    slowCallRate: Float,
  ) = Unit

  override fun recordCall(
    command: String,
    outcome: String,
  ) = Unit
}
