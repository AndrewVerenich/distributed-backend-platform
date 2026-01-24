package com.andver.time.starter.service

import org.slf4j.LoggerFactory
import java.time.Instant

interface LogicalTimeService {
  fun getLogicalTime(): Long
  fun getLogicalInstant(): Instant
}

class DefaultLogicalTimeService(
  private val offsetProvider: OffsetProvider,
) : LogicalTimeService {
  private val logger = LoggerFactory.getLogger(LogicalTimeService::class.java)

  override fun getLogicalTime(): Long {
    val localTime = System.currentTimeMillis()
    val offset = offsetProvider.getOffset()
    val logicalTime = localTime + offset

    logger.trace(
      "Logical time: local={}, offset={}, logical={}",
      localTime, offset, logicalTime
    )

    return logicalTime
  }

  override fun getLogicalInstant(): Instant {
    return Instant.ofEpochMilli(getLogicalTime())
  }
}

