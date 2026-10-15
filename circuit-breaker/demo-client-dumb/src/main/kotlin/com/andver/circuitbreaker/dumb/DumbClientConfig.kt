package com.andver.circuitbreaker.dumb

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
class DumbClientConfig {
  @Bean
  fun sharedConnectionProvider(
    @Value("\${demo.pool.max-connections:4}") maxConnections: Int,
  ): ConnectionProvider =
    ConnectionProvider
      .builder("dumb-shared")
      .maxConnections(maxConnections)
      .pendingAcquireMaxCount(1_000)
      .pendingAcquireTimeout(Duration.ofSeconds(10))
      .metrics(true)
      .build()

  @Bean
  fun dependencyWebClient(
    sharedConnectionProvider: ConnectionProvider,
    @Value("\${demo.dependency.base-url}") baseUrl: String,
  ): WebClient {
    val httpClient =
      HttpClient
        .create(sharedConnectionProvider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
        .responseTimeout(Duration.ofSeconds(15))
    return WebClient
      .builder()
      .baseUrl(baseUrl)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
  }

  @Bean
  fun demoTimers(meterRegistry: MeterRegistry): DemoTimers = DemoTimers(meterRegistry)
}

class DemoTimers(
  registry: MeterRegistry,
) {
  val checkout: Timer =
    Timer
      .builder("demo.request.duration")
      .tag("client", "dumb")
      .tag("endpoint", "checkout")
      .publishPercentileHistogram()
      .register(registry)

  val browse: Timer =
    Timer
      .builder("demo.request.duration")
      .tag("client", "dumb")
      .tag("endpoint", "browse")
      .publishPercentileHistogram()
      .register(registry)
}
