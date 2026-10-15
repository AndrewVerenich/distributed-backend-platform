package com.andver.circuitbreaker.core

import com.andver.circuitbreaker.metrics.CircuitBreakerMetrics
import com.andver.circuitbreaker.metrics.NoopCircuitBreakerMetrics
import java.util.concurrent.ConcurrentHashMap

class CircuitBreakerRegistry(
  private val configs: Map<String, CircuitBreakerConfig>,
  private val defaultConfig: CircuitBreakerConfig = CircuitBreakerConfig(),
  private val clock: NanoClock = NanoClock.SYSTEM,
  private val metrics: CircuitBreakerMetrics = NoopCircuitBreakerMetrics,
) {
  private val breakers = ConcurrentHashMap<String, CountBasedCircuitBreaker>()

  fun breaker(command: String): CountBasedCircuitBreaker =
    breakers.computeIfAbsent(command) { name ->
      CountBasedCircuitBreaker(
        name = name,
        config = configs[name] ?: defaultConfig,
        clock = clock,
        metrics = metrics,
      )
    }

  fun existing(command: String): CountBasedCircuitBreaker? = breakers[command]
}
