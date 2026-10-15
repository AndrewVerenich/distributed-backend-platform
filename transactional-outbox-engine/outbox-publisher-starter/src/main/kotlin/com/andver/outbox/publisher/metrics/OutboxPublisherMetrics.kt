package com.andver.outbox.publisher.metrics

import java.time.Duration

interface OutboxPublisherMetrics {
  fun recordPublished(eventType: String)
  fun recordPublishDuration(eventType: String, duration: Duration)

  companion object {
    val NOOP: OutboxPublisherMetrics = object : OutboxPublisherMetrics {
      override fun recordPublished(eventType: String) = Unit
      override fun recordPublishDuration(eventType: String, duration: Duration) = Unit
    }
  }
}
