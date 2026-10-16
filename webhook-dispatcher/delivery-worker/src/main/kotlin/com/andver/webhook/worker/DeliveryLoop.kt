package com.andver.webhook.worker

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class DeliveryLoop(
  private val deliveryService: DeliveryService,
) {
  private val log = LoggerFactory.getLogger(javaClass)
  private val reclaiming = AtomicBoolean(false)

  @Scheduled(fixedDelayString = "\${webhook.delivery.poll-interval-ms:500}")
  fun poll() {
    try {
      // Drain a few claims per tick so slow endpoints don't starve the loop entirely.
      repeat(10) {
        val claimed = deliveryService.claimAndDeliver().block() ?: false
        if (!claimed) return
      }
    } catch (e: Exception) {
      log.error("delivery poll failed: {}", e.message, e)
    }
  }

  @Scheduled(fixedDelayString = "\${webhook.delivery.reclaim-interval-ms:15000}")
  fun reclaim() {
    if (!reclaiming.compareAndSet(false, true)) return
    try {
      val n = deliveryService.reclaimStale().block() ?: 0
      if (n > 0) log.info("reclaimed {} stale in_flight attempts", n)
    } catch (e: Exception) {
      log.error("reclaim failed: {}", e.message, e)
    } finally {
      reclaiming.set(false)
    }
  }
}
