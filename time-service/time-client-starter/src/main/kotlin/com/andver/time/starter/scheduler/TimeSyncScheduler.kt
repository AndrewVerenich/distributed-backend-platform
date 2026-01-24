package com.andver.time.starter.scheduler

import com.andver.time.starter.client.TimeServiceClient
import com.andver.time.starter.service.OffsetProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import kotlin.math.abs

private const val DRIFT_THRESHOLD = 100

class TimeSyncScheduler(
  private val timeServiceClient: TimeServiceClient,
  private val offsetProvider: OffsetProvider,
  @Value("\${time.node-id:unknown}") private val nodeId: String,
) {
  private val logger = LoggerFactory.getLogger(TimeSyncScheduler::class.java)

  init {
    performSync()
  }

  @Scheduled(fixedDelayString = "\${time.sync.interval-ms:30000}")
  fun scheduledSync() {
    performSync()
  }

  fun performSync() {
    val clientT0 = System.currentTimeMillis()
    timeServiceClient.startSync(nodeId, clientT0)
      .flatMap {
        val clientT3 = System.currentTimeMillis()
        timeServiceClient.completeSync(nodeId, clientT0, clientT3)
      }
      .doOnSuccess { response ->
        val newOffset = response.offset
        val oldOffset = offsetProvider.getOffset()

        offsetProvider.updateOffset(newOffset)

        val drift = abs(newOffset - oldOffset)
        if (drift > DRIFT_THRESHOLD) {
          logger.warn("Significant clock drift detected: {}ms (old={}, new={})", drift, oldOffset, newOffset)
        }
        logger.info("Sync completed: offset={}ms, RTT={}ms", newOffset, response.roundTripTime)
      }
      .doOnError { error ->
        logger.error("Failed to sync with Time Service", error)
      }
      .subscribe()
  }
}

