package com.andver.circuitbreaker.core

enum class CircuitBreakerState {
  CLOSED,
  OPEN,
  HALF_OPEN,
  ;

  fun metricValue(): Int =
    when (this) {
      CLOSED -> 0
      OPEN -> 1
      HALF_OPEN -> 2
    }
}
