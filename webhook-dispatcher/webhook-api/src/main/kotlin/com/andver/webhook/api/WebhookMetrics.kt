package com.andver.webhook.api

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class WebhookMetrics(registry: MeterRegistry) {
  private val accepted = Counter.builder("webhook_events_accepted").register(registry)
  private val duplicates = Counter.builder("webhook_events_duplicate").register(registry)
  private val created = Counter.builder("webhook_webhooks_created").register(registry)

  fun eventAccepted() = accepted.increment()
  fun eventDuplicate() = duplicates.increment()
  fun webhookCreated() = created.increment()
}
