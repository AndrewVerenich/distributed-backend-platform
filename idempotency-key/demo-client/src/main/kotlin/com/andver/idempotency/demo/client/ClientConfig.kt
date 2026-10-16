package com.andver.idempotency.demo.client

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(ChargeClientProperties::class)
class ClientConfig {

  @Bean
  fun chargeWebClient(
    builder: WebClient.Builder,
    properties: ChargeClientProperties,
  ): WebClient {
    val httpClient = HttpClient.create()
      .responseTimeout(properties.responseTimeout)
    return builder
      .baseUrl(properties.baseUrl)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
  }
}

@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "charge")
data class ChargeClientProperties(
  val baseUrl: String = "http://localhost:8220",
  val responseTimeout: Duration = Duration.ofMillis(500),
)
