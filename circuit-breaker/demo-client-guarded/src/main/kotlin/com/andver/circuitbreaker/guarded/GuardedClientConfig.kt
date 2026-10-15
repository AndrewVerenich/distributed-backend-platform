package com.andver.circuitbreaker.guarded

import com.andver.circuitbreaker.filter.WebClientCircuitBreakerFilter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

@Configuration
class GuardedClientConfig {
  @Bean
  fun paymentConnectionProvider(
    @Value("\${demo.pool.max-connections:4}") maxConnections: Int,
  ): ConnectionProvider =
    ConnectionProvider
      .builder("guarded-payment")
      .maxConnections(maxConnections)
      .pendingAcquireMaxCount(1_000)
      .pendingAcquireTimeout(Duration.ofSeconds(10))
      .metrics(true)
      .build()

  @Bean
  fun catalogConnectionProvider(
    @Value("\${demo.pool.max-connections:4}") maxConnections: Int,
  ): ConnectionProvider =
    ConnectionProvider
      .builder("guarded-catalog")
      .maxConnections(maxConnections)
      .pendingAcquireMaxCount(1_000)
      .pendingAcquireTimeout(Duration.ofSeconds(10))
      .metrics(true)
      .build()

  @Bean
  fun paymentWebClient(
    paymentConnectionProvider: ConnectionProvider,
    circuitBreakerFilter: WebClientCircuitBreakerFilter,
    @Value("\${demo.dependency.base-url}") baseUrl: String,
  ): WebClient = buildClient(paymentConnectionProvider, circuitBreakerFilter, baseUrl)

  @Bean
  fun catalogWebClient(
    catalogConnectionProvider: ConnectionProvider,
    circuitBreakerFilter: WebClientCircuitBreakerFilter,
    @Value("\${demo.dependency.base-url}") baseUrl: String,
  ): WebClient = buildClient(catalogConnectionProvider, circuitBreakerFilter, baseUrl)

  @Bean
  fun demoTimers(meterRegistry: MeterRegistry): DemoTimers = DemoTimers(meterRegistry)

  private fun buildClient(
    provider: ConnectionProvider,
    filter: WebClientCircuitBreakerFilter,
    baseUrl: String,
  ): WebClient {
    val httpClient =
      HttpClient
        .create(provider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
        .responseTimeout(Duration.ofSeconds(15))
    return WebClient
      .builder()
      .baseUrl(baseUrl)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .filter(filter)
      .build()
  }
}

class DemoTimers(
  registry: MeterRegistry,
) {
  val checkout: Timer =
    Timer
      .builder("demo.request.duration")
      .tag("client", "guarded")
      .tag("endpoint", "checkout")
      .publishPercentileHistogram()
      .register(registry)

  val browse: Timer =
    Timer
      .builder("demo.request.duration")
      .tag("client", "guarded")
      .tag("endpoint", "browse")
      .publishPercentileHistogram()
      .register(registry)
}
