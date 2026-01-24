package com.andver.timeservice.service

import com.andver.timeservice.model.SyncRequest
import com.andver.timeservice.model.SyncResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

interface ClockSynchronizationService {
  fun handleSyncRequest(request: SyncRequest): Long
  fun completeSync(nodeId: String, clientT0: Long, clientT3: Long): SyncResponse
}

@Service
class DefaultClockSynchronizationService(
  private val timeService: TimeService
) : ClockSynchronizationService {
  private val logger = LoggerFactory.getLogger(ClockSynchronizationService::class.java)

  private val requestTimes = ConcurrentHashMap<String, Long>()

  override fun handleSyncRequest(request: SyncRequest): Long {
    val serverTime = timeService.getCurrentTime()
    requestTimes[request.nodeId] = serverTime
    logger.info("Sync request from node ${request.nodeId}: localTime=${request.localTime}, serverTime=$serverTime")
    return serverTime
  }

  override fun completeSync(nodeId: String, clientT0: Long, clientT3: Long): SyncResponse {
    val serverT1 = requestTimes[nodeId] ?: run {
      logger.warn("No request time found for node $nodeId")
      return SyncResponse(
        serverTime = timeService.getCurrentTime(),
        roundTripTime = 0,
        offset = 0
      )
    }

    val serverT2 = timeService.getCurrentTime()

    // Вычисляем round-trip time
    val roundTripTime = (clientT3 - clientT0) - (serverT2 - serverT1)

    // Вычисляем offset
    // offset = serverTime - clientTime - (RTT / 2)
    val serverTimeMidpoint = (serverT1 + serverT2) / 2
    val clientTimeMidpoint = (clientT0 + clientT3) / 2
    val offset = serverTimeMidpoint - clientTimeMidpoint - (roundTripTime / 2)

    requestTimes.remove(nodeId)

    logger.info("Sync completed for node $nodeId: offset=$offset ms, RTT=$roundTripTime ms")

    return SyncResponse(
      serverTime = serverTimeMidpoint,
      roundTripTime = roundTripTime,
      offset = offset
    )
  }
}

