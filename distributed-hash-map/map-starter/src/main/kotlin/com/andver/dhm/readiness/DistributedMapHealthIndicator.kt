package com.andver.dhm.readiness

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator

/**
 * `UP` only when every configured map has completed bootstrap restore. Exposed under
 * `/actuator/health` and contributes to readiness via Spring Boot's default group setup.
 */
class DistributedMapHealthIndicator(
  private val tracker: MapReadinessTracker,
) : HealthIndicator {

  override fun health(): Health {
    val snapshot = tracker.snapshot()
    val allReady = snapshot.values.all { it }
    val builder = if (allReady) Health.up() else Health.outOfService()
    builder.withDetail("maps", snapshot)
    return builder.build()
  }
}
