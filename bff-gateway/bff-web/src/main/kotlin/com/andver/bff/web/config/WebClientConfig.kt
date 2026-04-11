package com.andver.bff.web.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig(
  private val backendProperties: BackendProperties,
) {

  @Bean
  fun userWebClient(): WebClient = webClient(backendProperties.userServiceBaseUrl)

  @Bean
  fun productWebClient(): WebClient = webClient(backendProperties.productServiceBaseUrl)

  private fun webClient(baseUrl: String): WebClient {
    val strategies = ExchangeStrategies.builder()
      .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
      .build()
    val httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(10))
    return WebClient.builder()
      .baseUrl(baseUrl)
      .exchangeStrategies(strategies)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
  }
}
