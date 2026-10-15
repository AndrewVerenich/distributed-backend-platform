package com.andver.clientratelimit.filter

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

class ClientRateLimitWebClientCustomizer(
  private val filter: ExchangeFilterFunction,
) : WebClientCustomizer {
  override fun customize(builder: WebClient.Builder) {
    builder.filter(filter)
  }
}
