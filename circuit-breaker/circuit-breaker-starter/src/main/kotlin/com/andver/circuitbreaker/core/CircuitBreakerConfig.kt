package com.andver.circuitbreaker.core

import java.time.Duration

data class CircuitBreakerConfig(
  val slidingWindowSize: Int = 10,
  val minimumNumberOfCalls: Int = 5,
  val failureRateThreshold: Float = 50f,
  val slowCallRateThreshold: Float = 50f,
  val slowCallDuration: Duration = Duration.ofSeconds(2),
  val waitDurationInOpenState: Duration = Duration.ofSeconds(5),
  val permittedNumberOfCallsInHalfOpenState: Int = 3,
) {
  init {
    require(slidingWindowSize > 0) { "slidingWindowSize must be > 0" }
    require(minimumNumberOfCalls > 0) { "minimumNumberOfCalls must be > 0" }
    require(minimumNumberOfCalls <= slidingWindowSize) {
      "minimumNumberOfCalls must be <= slidingWindowSize"
    }
    require(failureRateThreshold in 0f..100f) { "failureRateThreshold must be in 0..100" }
    require(slowCallRateThreshold in 0f..100f) { "slowCallRateThreshold must be in 0..100" }
    require(!slowCallDuration.isNegative && !slowCallDuration.isZero) {
      "slowCallDuration must be > 0"
    }
    require(!waitDurationInOpenState.isNegative && !waitDurationInOpenState.isZero) {
      "waitDurationInOpenState must be > 0"
    }
    require(permittedNumberOfCallsInHalfOpenState > 0) {
      "permittedNumberOfCallsInHalfOpenState must be > 0"
    }
  }

  val slowCallDurationNanos: Long = slowCallDuration.toNanos()
  val waitDurationNanos: Long = waitDurationInOpenState.toNanos()
}
