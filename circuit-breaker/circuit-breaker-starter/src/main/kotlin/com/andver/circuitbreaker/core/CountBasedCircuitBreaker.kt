package com.andver.circuitbreaker.core

import com.andver.circuitbreaker.metrics.CircuitBreakerMetrics
import com.andver.circuitbreaker.metrics.NoopCircuitBreakerMetrics

/**
 * Count-based circuit breaker with slow-call detection.
 *
 * Closed → Open when failure rate or slow-call rate crosses the threshold
 * after [CircuitBreakerConfig.minimumNumberOfCalls] samples in the ring buffer.
 * Open → Half-open lazily on the next acquire after [CircuitBreakerConfig.waitDurationInOpenState].
 * Half-open evaluates after exactly [CircuitBreakerConfig.permittedNumberOfCallsInHalfOpenState]
 * probe results using the same thresholds.
 */
class CountBasedCircuitBreaker(
  val name: String,
  private val config: CircuitBreakerConfig,
  private val clock: NanoClock = NanoClock.SYSTEM,
  private val metrics: CircuitBreakerMetrics = NoopCircuitBreakerMetrics,
) {
  internal val lock = Any()

  private var state: CircuitBreakerState = CircuitBreakerState.CLOSED
  private var openedAtNanos: Long = 0L

  private val failures = BooleanArray(config.slidingWindowSize)
  private val slows = BooleanArray(config.slidingWindowSize)
  private var head = 0
  private var size = 0
  private var failureCount = 0
  private var slowCount = 0

  private var halfOpenInFlight = 0
  private var halfOpenCompleted = 0
  private var halfOpenFailures = 0
  private var halfOpenSlows = 0

  init {
    metrics.bind(name)
    publishSnapshot()
  }

  fun state(): CircuitBreakerState = synchronized(lock) { state }

  fun failureRate(): Float = synchronized(lock) { currentFailureRate() }

  fun slowCallRate(): Float = synchronized(lock) { currentSlowCallRate() }

  fun tryAcquire(): AcquireResult =
    synchronized(lock) {
      when (state) {
        CircuitBreakerState.CLOSED -> AcquireResult.Permitted(this, probe = false)

        CircuitBreakerState.OPEN -> {
          if (clock.nanoTime() - openedAtNanos < config.waitDurationNanos) {
            metrics.recordCall(name, "rejected")
            publishSnapshot()
            AcquireResult.Rejected
          } else {
            enterHalfOpen()
            halfOpenInFlight++
            AcquireResult.Permitted(this, probe = true)
          }
        }

        CircuitBreakerState.HALF_OPEN -> {
          if (halfOpenInFlight + halfOpenCompleted < config.permittedNumberOfCallsInHalfOpenState) {
            halfOpenInFlight++
            AcquireResult.Permitted(this, probe = true)
          } else {
            metrics.recordCall(name, "rejected")
            publishSnapshot()
            AcquireResult.Rejected
          }
        }
      }
    }

  internal fun onHttpStatus(
    probe: Boolean,
    status: Int,
    durationNanos: Long,
  ) {
    val failure = status >= 500
    val slow = durationNanos >= config.slowCallDurationNanos
    recordOutcome(probe, failure, slow)
  }

  internal fun onError(
    probe: Boolean,
    durationNanos: Long,
  ) {
    val slow = durationNanos >= config.slowCallDurationNanos
    recordOutcome(probe, failure = true, slow = slow)
  }

  internal fun onRelease(probe: Boolean) {
    synchronized(lock) {
      if (probe && state == CircuitBreakerState.HALF_OPEN) {
        halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
      }
      publishSnapshot()
    }
  }

  private fun recordOutcome(
    probe: Boolean,
    failure: Boolean,
    slow: Boolean,
  ) {
    synchronized(lock) {
      if (failure) metrics.recordCall(name, "failure")
      if (slow) metrics.recordCall(name, "slow")
      if (!failure && !slow) metrics.recordCall(name, "success")
      // A call can be both failure and slow; success is only when neither.

      if (probe) {
        recordProbe(failure, slow)
      } else {
        recordClosedWindow(failure, slow)
      }
      publishSnapshot()
    }
  }

  private fun recordClosedWindow(
    failure: Boolean,
    slow: Boolean,
  ) {
    pushWindow(failure, slow)
    // In-flight from Closed still fills the main window, but only Closed may open the circuit.
    if (
      state == CircuitBreakerState.CLOSED &&
      size >= config.minimumNumberOfCalls &&
      crossesThreshold(currentFailureRate(), currentSlowCallRate())
    ) {
      tripOpen()
    }
  }

  private fun recordProbe(
    failure: Boolean,
    slow: Boolean,
  ) {
    if (state != CircuitBreakerState.HALF_OPEN) {
      // State moved under us; still count toward the main window for observability.
      pushWindow(failure, slow)
      return
    }

    halfOpenInFlight = (halfOpenInFlight - 1).coerceAtLeast(0)
    halfOpenCompleted++
    if (failure) halfOpenFailures++
    if (slow) halfOpenSlows++

    if (halfOpenCompleted < config.permittedNumberOfCallsInHalfOpenState) {
      return
    }

    val probes = halfOpenCompleted
    val failureRate = halfOpenFailures * 100f / probes
    val slowRate = halfOpenSlows * 100f / probes
    if (crossesThreshold(failureRate, slowRate)) {
      tripOpen()
    } else {
      enterClosed()
    }
  }

  private fun pushWindow(
    failure: Boolean,
    slow: Boolean,
  ) {
    if (size == config.slidingWindowSize) {
      if (failures[head]) failureCount--
      if (slows[head]) slowCount--
    } else {
      size++
    }
    failures[head] = failure
    slows[head] = slow
    if (failure) failureCount++
    if (slow) slowCount++
    head = (head + 1) % config.slidingWindowSize
  }

  private fun crossesThreshold(
    failureRate: Float,
    slowRate: Float,
  ): Boolean =
    failureRate >= config.failureRateThreshold || slowRate >= config.slowCallRateThreshold

  private fun currentFailureRate(): Float =
    if (size == 0) 0f else failureCount * 100f / size

  private fun currentSlowCallRate(): Float =
    if (size == 0) 0f else slowCount * 100f / size

  private fun tripOpen() {
    state = CircuitBreakerState.OPEN
    openedAtNanos = clock.nanoTime()
    resetHalfOpenCounters()
  }

  private fun enterHalfOpen() {
    state = CircuitBreakerState.HALF_OPEN
    resetHalfOpenCounters()
  }

  private fun enterClosed() {
    state = CircuitBreakerState.CLOSED
    resetWindow()
    resetHalfOpenCounters()
  }

  private fun resetWindow() {
    head = 0
    size = 0
    failureCount = 0
    slowCount = 0
  }

  private fun resetHalfOpenCounters() {
    halfOpenInFlight = 0
    halfOpenCompleted = 0
    halfOpenFailures = 0
    halfOpenSlows = 0
  }

  private fun publishSnapshot() {
    metrics.onState(name, state)
    metrics.onRates(name, currentFailureRate(), currentSlowCallRate())
  }
}
