package com.andver.singleflight

import com.andver.singleflight.internal.FlightCodec
import com.andver.singleflight.internal.SingleflightSharedException
import com.andver.singleflight.metrics.NoopSingleflightMetrics
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class LocalSingleflightTest {

  private val codec = FlightCodec(jacksonObjectMapper())

  @Test
  fun `coalesces concurrent loads to a single upstream call`() {
    val sf = local()
    val loads = AtomicInteger(0)

    val loader = {
      Mono.delay(Duration.ofMillis(80))
        .map {
          loads.incrementAndGet()
          "value"
        }
    }

    val outcomes = Flux.range(1, 50)
      .flatMap({ sf.execute("hot", String::class.java, loader) }, 50)
      .collectList()
      .block(Duration.ofSeconds(5))!!

    assertEquals(50, outcomes.size)
    assertTrue(outcomes.all { it.value == "value" })
    assertEquals(1, loads.get())
    assertEquals(1, outcomes.count { it.role == SingleflightRole.LEADER })
    assertEquals(49, outcomes.count { it.role == SingleflightRole.WAITER })
  }

  @Test
  fun `shares errors with waiters when shareErrors is true`() {
    val sf = local(shareErrors = true)
    val loads = AtomicInteger(0)

    val loader = {
      Mono.delay(Duration.ofMillis(80))
        .flatMap<String> {
          loads.incrementAndGet()
          Mono.error(IllegalStateException("boom"))
        }
    }

    val signals = Flux.range(1, 20)
      .flatMap({ sf.execute("k", String::class.java, loader).materialize() }, 20)
      .collectList()
      .block(Duration.ofSeconds(5))!!

    assertEquals(1, loads.get())
    assertEquals(20, signals.size)
    assertTrue(signals.all { it.isOnError })
    val errors = signals.mapNotNull { it.throwable }
    assertTrue(errors.any { it is IllegalStateException })
    assertTrue(errors.any { it is SingleflightSharedException })
  }

  @Test
  fun `waiters re-elect when shareErrors is false`() {
    val sf = local(shareErrors = false)
    val loads = AtomicInteger(0)

    val loader = {
      Mono.fromCallable {
        val n = loads.incrementAndGet()
        if (n == 1) throw IllegalStateException("transient")
        "ok"
      }.subscribeOn(Schedulers.boundedElastic())
    }

    val leader = sf.execute("k", String::class.java, loader)
    // waiter joins while first flight is still the failing leader, then re-elects
    val waiter = Mono.delay(Duration.ofMillis(5))
      .flatMap { sf.execute("k", String::class.java, loader) }

    StepVerifier.create(leader).expectError(IllegalStateException::class.java).verify()
    StepVerifier.create(waiter)
      .assertNext { assertEquals("ok", it.value) }
      .verifyComplete()

    assertEquals(2, loads.get())
  }

  @Test
  fun `passthrough does not coalesce`() {
    val sf = PassthroughSingleflight(NoopSingleflightMetrics)
    val loads = AtomicInteger(0)
    val loader = {
      Mono.delay(Duration.ofMillis(30)).map {
        loads.incrementAndGet()
        "v"
      }
    }

    StepVerifier.create(
      Mono.zip(
        sf.execute("k", String::class.java, loader),
        sf.execute("k", String::class.java, loader),
      ),
    )
      .assertNext { assertEquals(2, loads.get()) }
      .verifyComplete()
  }

  private fun local(shareErrors: Boolean = true): LocalSingleflight =
    LocalSingleflight(
      SingleflightProperties(shareErrors = shareErrors, waiterTimeout = Duration.ofSeconds(3)),
      NoopSingleflightMetrics,
      codec,
    )
}
