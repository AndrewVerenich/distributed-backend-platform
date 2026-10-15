package com.andver.outbox.consumer.metrics

import java.time.Duration

interface OutboxConsumerMetrics {
  fun recordConsumed(eventType: String)
  fun recordHandlerMissing(eventType: String)
  fun recordProcessed(eventType: String, result: String)
  fun recordProcessDuration(eventType: String, duration: Duration)

  companion object {
    val NOOP: OutboxConsumerMetrics = object : OutboxConsumerMetrics {
      override fun recordConsumed(eventType: String) = Unit
      override fun recordHandlerMissing(eventType: String) = Unit
      override fun recordProcessed(eventType: String, result: String) = Unit
      override fun recordProcessDuration(eventType: String, duration: Duration) = Unit
    }
  }
}
