package com.andver.webhook.worker

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "webhook.delivery")
data class WorkerProperties(
  val maxAttempts: Int = 5,
  val backoffSeconds: List<Long> = listOf(3, 8, 20),
  val maxInFlightPerWebhook: Int = 2,
  val leaseSeconds: Long = 60,
  val pauseAfterTransportFailures: Int = 3,
  val httpTimeoutSeconds: Long = 5,
  val allowPrivateUrls: Boolean = true,
  val pollIntervalMs: Long = 500,
)
