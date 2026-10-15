package com.andver.circuitbreaker.filter

import com.andver.circuitbreaker.core.CircuitBreakerConfig
import com.andver.circuitbreaker.core.CircuitBreakerOpenException
import com.andver.circuitbreaker.core.CircuitBreakerRegistry
import com.andver.circuitbreaker.core.CircuitBreakerState
import com.andver.circuitbreaker.core.MutableNanoClock
import com.andver.circuitbreaker.filter.circuitBreakerCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class WebClientCircuitBreakerFilterTest {
  private val clock = MutableNanoClock()

  private fun registry(): CircuitBreakerRegistry =
    CircuitBreakerRegistry(
      configs = mapOf("payment" to CircuitBreakerConfig()),
      defaultConfig = CircuitBreakerConfig(),
      clock = clock,
    )

  @Test
  fun `open circuit does not subscribe to exchange function`() {
    val registry = registry()
    val filter = WebClientCircuitBreakerFilter(registry, clock)
    val subscriptions = AtomicInteger()

    // Trip open (opens at minimumNumberOfCalls with 100% failures).
    for (i in 1..10) {
      when (val result = registry.breaker("payment").tryAcquire()) {
        is com.andver.circuitbreaker.core.AcquireResult.Permitted -> result.recordError(1L)
        com.andver.circuitbreaker.core.AcquireResult.Rejected -> break
      }
    }
    assertThat(registry.breaker("payment").state()).isEqualTo(CircuitBreakerState.OPEN)

    val request =
      ClientRequest
        .create(org.springframework.http.HttpMethod.GET, URI.create("http://payment/pay"))
        .circuitBreakerCommand("payment")
        .build()

    val next =
      ExchangeFunction {
        subscriptions.incrementAndGet()
        Mono.just(ClientResponse.create(HttpStatus.OK).build())
      }

    StepVerifier
      .create(filter.filter(request, next))
      .expectError(CircuitBreakerOpenException::class.java)
      .verify()

    assertThat(subscriptions.get()).isZero()
  }

  @Test
  fun `records slow successful response`() {
    val registry = registry()
    val filter = WebClientCircuitBreakerFilter(registry, clock)
    val request =
      ClientRequest
        .create(org.springframework.http.HttpMethod.GET, URI.create("http://payment/pay"))
        .circuitBreakerCommand("payment")
        .build()

    val next =
      ExchangeFunction {
        clock.advance(Duration.ofSeconds(2).toNanos())
        Mono.just(ClientResponse.create(HttpStatus.OK).build())
      }

    repeat(5) {
      StepVerifier
        .create(filter.filter(request, next))
        .expectNextCount(1)
        .verifyComplete()
    }

    assertThat(registry.breaker("payment").state()).isEqualTo(CircuitBreakerState.OPEN)
    assertThat(registry.breaker("payment").slowCallRate()).isEqualTo(100f)
  }

  @Test
  fun `request without command attribute bypasses breaker`() {
    val registry = registry()
    val filter = WebClientCircuitBreakerFilter(registry, clock)
    val request =
      ClientRequest
        .create(org.springframework.http.HttpMethod.GET, URI.create("http://payment/pay"))
        .build()

    val next =
      ExchangeFunction {
        Mono.just(ClientResponse.create(HttpStatus.OK).build())
      }

    StepVerifier
      .create(filter.filter(request, next))
      .expectNextCount(1)
      .verifyComplete()

    assertThat(registry.existing("payment")).isNull()
  }
}
