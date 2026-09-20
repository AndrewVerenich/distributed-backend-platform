package com.andver.waitingroom.checkout.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(CheckoutProperties::class)
class CheckoutConfig

@ConfigurationProperties(prefix = "checkout")
data class CheckoutProperties(
  /** Artificial processing latency to saturate under direct load. */
  val latency: Duration = Duration.ofMillis(200),
  /** Max concurrent checkouts; excess fail with 503. */
  val maxConcurrency: Int = 20,
)
