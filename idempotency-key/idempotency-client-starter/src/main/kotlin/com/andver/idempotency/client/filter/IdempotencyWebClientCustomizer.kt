package com.andver.idempotency.client.filter

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.web.reactive.function.client.WebClient

class IdempotencyWebClientCustomizer(
  private val filter: IdempotencyExchangeFilter,
) : WebClientCustomizer {
  override fun customize(webClientBuilder: WebClient.Builder) {
    webClientBuilder.filter(filter)
  }
}
