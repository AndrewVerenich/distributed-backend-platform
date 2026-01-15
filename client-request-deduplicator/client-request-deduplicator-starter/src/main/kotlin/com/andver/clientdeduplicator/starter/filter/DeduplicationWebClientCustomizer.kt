package com.andver.clientdeduplicator.starter.filter

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

class DeduplicationWebClientCustomizer(private val deduplicationFilter: ExchangeFilterFunction) : WebClientCustomizer {
  override fun customize(builder: WebClient.Builder) {
    builder.filter(deduplicationFilter)
  }
}