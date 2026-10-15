package com.andver.circuitbreaker.core

class CircuitBreakerOpenException(
  val command: String,
) : RuntimeException("Circuit breaker open for command='$command'")
