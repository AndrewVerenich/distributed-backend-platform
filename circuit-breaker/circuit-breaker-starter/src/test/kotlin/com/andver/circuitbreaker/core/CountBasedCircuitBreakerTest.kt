package com.andver.circuitbreaker.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CountBasedCircuitBreakerTest {
  private val clock = MutableNanoClock()

  private fun breaker(
    window: Int = 10,
    minCalls: Int = 5,
    failureThreshold: Float = 50f,
    slowThreshold: Float = 50f,
    slowDuration: Duration = Duration.ofSeconds(2),
    wait: Duration = Duration.ofSeconds(5),
    halfOpenPermits: Int = 3,
  ): CountBasedCircuitBreaker =
    CountBasedCircuitBreaker(
      name = "payment",
      config =
        CircuitBreakerConfig(
          slidingWindowSize = window,
          minimumNumberOfCalls = minCalls,
          failureRateThreshold = failureThreshold,
          slowCallRateThreshold = slowThreshold,
          slowCallDuration = slowDuration,
          waitDurationInOpenState = wait,
          permittedNumberOfCallsInHalfOpenState = halfOpenPermits,
        ),
      clock = clock,
    )

  private fun AcquireResult.asPermit(): AcquireResult.Permitted =
    this as AcquireResult.Permitted

  private fun fail(
    breaker: CountBasedCircuitBreaker,
    times: Int,
    durationNanos: Long = 1L,
  ) {
    repeat(times) {
      when (val result = breaker.tryAcquire()) {
        is AcquireResult.Permitted -> result.recordError(durationNanos)
        AcquireResult.Rejected -> return
      }
    }
  }

  private fun succeed(
    breaker: CountBasedCircuitBreaker,
    times: Int,
    durationNanos: Long = 1L,
  ) {
    repeat(times) {
      when (val result = breaker.tryAcquire()) {
        is AcquireResult.Permitted -> result.recordHttpStatus(200, durationNanos)
        AcquireResult.Rejected -> return
      }
    }
  }

  private fun tripOpen(breaker: CountBasedCircuitBreaker) {
    fail(breaker, 10)
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN)
  }

  @Test
  fun `does not open before minimum number of calls`() {
    val cb = breaker(minCalls = 5)
    fail(cb, 4)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
    assertThat(cb.tryAcquire()).isInstanceOf(AcquireResult.Permitted::class.java)
  }

  @Test
  fun `opens on failure rate threshold`() {
    val cb = breaker()
    succeed(cb, 5)
    fail(cb, 5)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN)
    assertThat(cb.failureRate()).isEqualTo(50f)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)
  }

  @Test
  fun `opens on slow calls with HTTP 200`() {
    val cb = breaker()
    val slow = Duration.ofSeconds(2).toNanos()
    succeed(cb, 10, durationNanos = slow)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN)
    assertThat(cb.slowCallRate()).isEqualTo(100f)
    assertThat(cb.failureRate()).isEqualTo(0f)
  }

  @Test
  fun `fast 4xx does not count as failure`() {
    val cb = breaker()
    repeat(10) {
      cb.tryAcquire().asPermit().recordHttpStatus(404, 1L)
    }
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
    assertThat(cb.failureRate()).isEqualTo(0f)
  }

  @Test
  fun `old slots are evicted and rate drops`() {
    val cb = breaker(failureThreshold = 100f, slowThreshold = 100f)
    repeat(5) {
      cb.tryAcquire().asPermit().recordError(1L)
      cb.tryAcquire().asPermit().recordHttpStatus(200, 1L)
    }
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
    assertThat(cb.failureRate()).isEqualTo(50f)

    succeed(cb, 10)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
    assertThat(cb.failureRate()).isEqualTo(0f)
  }

  @Test
  fun `open rejects until wait duration then allows half-open probe`() {
    val cb = breaker()
    tripOpen(cb)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)

    clock.advanceMillis(4_999)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)

    clock.advanceMillis(1)
    val probe = cb.tryAcquire()
    assertThat(probe).isInstanceOf(AcquireResult.Permitted::class.java)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.HALF_OPEN)
  }

  @Test
  fun `half-open allows at most N probes even under contention`() {
    val cb = breaker(halfOpenPermits = 3)
    tripOpen(cb)
    clock.advanceMillis(5_000)

    val pool = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    val acquired = AtomicInteger()
    val rejected = AtomicInteger()
    val done = CountDownLatch(8)
    repeat(8) {
      pool.submit {
        start.await()
        when (cb.tryAcquire()) {
          AcquireResult.Rejected -> rejected.incrementAndGet()
          is AcquireResult.Permitted -> acquired.incrementAndGet()
        }
        done.countDown()
      }
    }
    start.countDown()
    assertThat(done.await(2, TimeUnit.SECONDS)).isTrue()
    pool.shutdownNow()

    assertThat(acquired.get()).isEqualTo(3)
    assertThat(rejected.get()).isEqualTo(5)
  }

  @Test
  fun `half-open with one failure out of three returns to closed`() {
    val cb = breaker(halfOpenPermits = 3, failureThreshold = 50f)
    tripOpen(cb)
    clock.advanceMillis(5_000)

    cb.tryAcquire().asPermit().recordError(1L)
    cb.tryAcquire().asPermit().recordHttpStatus(200, 1L)
    cb.tryAcquire().asPermit().recordHttpStatus(200, 1L)

    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
    assertThat(cb.failureRate()).isEqualTo(0f)
  }

  @Test
  fun `half-open with two failures out of three reopens and resets wait timer`() {
    val cb = breaker(halfOpenPermits = 3, failureThreshold = 50f)
    tripOpen(cb)
    clock.advanceMillis(5_000)

    cb.tryAcquire().asPermit().recordError(1L)
    cb.tryAcquire().asPermit().recordError(1L)
    cb.tryAcquire().asPermit().recordHttpStatus(200, 1L)

    assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)

    clock.advanceMillis(4_999)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)
    clock.advanceMillis(1)
    assertThat(cb.tryAcquire()).isInstanceOf(AcquireResult.Permitted::class.java)
  }

  @Test
  fun `in-flight closed call does not consume half-open probe budget`() {
    val cb = breaker(halfOpenPermits = 3)
    // 5 successes + 4 failures = 44% — still Closed.
    succeed(cb, 5)
    fail(cb, 4)
    val inFlight = cb.tryAcquire().asPermit()

    // Tip over to Open with a separate call (5/10 = 50%).
    cb.tryAcquire().asPermit().recordError(1L)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN)

    clock.advanceMillis(5_000)
    val probes = (1..3).map { cb.tryAcquire() }
    assertThat(probes).allMatch { it is AcquireResult.Permitted }
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)

    // Completing the old Closed call must not steal a probe slot.
    inFlight.recordHttpStatus(200, 1L)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.HALF_OPEN)
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)
  }

  @Test
  fun `cancelled probe returns budget and does not stuck half-open`() {
    val cb = breaker(halfOpenPermits = 3)
    tripOpen(cb)
    clock.advanceMillis(5_000)

    val cancelled = cb.tryAcquire().asPermit()
    cancelled.release()

    val p1 = cb.tryAcquire().asPermit()
    val p2 = cb.tryAcquire().asPermit()
    val p3 = cb.tryAcquire().asPermit()
    assertThat(cb.tryAcquire()).isEqualTo(AcquireResult.Rejected)

    p1.recordHttpStatus(200, 1L)
    p2.recordHttpStatus(200, 1L)
    p3.recordHttpStatus(200, 1L)
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED)
  }

  @Test
  fun `slow 5xx counts as both failure and slow`() {
    val cb = breaker()
    val slow = Duration.ofSeconds(2).toNanos()
    for (i in 1..10) {
      when (val result = cb.tryAcquire()) {
        is AcquireResult.Permitted -> result.recordHttpStatus(503, slow)
        AcquireResult.Rejected -> break
      }
    }
    assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN)
    assertThat(cb.failureRate()).isEqualTo(100f)
    assertThat(cb.slowCallRate()).isEqualTo(100f)
  }
}
