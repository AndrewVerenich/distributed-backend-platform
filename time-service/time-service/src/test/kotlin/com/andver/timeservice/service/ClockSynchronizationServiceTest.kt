package com.andver.timeservice.service

import com.andver.timeservice.model.SyncRequest
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ClockSynchronizationServiceTest {

  private val timeService = mockk<TimeService>()
  private lateinit var syncService: DefaultClockSynchronizationService

  @BeforeEach
  fun setUp() {
    syncService = DefaultClockSynchronizationService(timeService)
  }

  @Test
  fun `handleSyncRequest records server time and returns it`() {
    val serverTime = 1_700_000_000_000L
    every { timeService.getCurrentTime() } returns serverTime

    val result = syncService.handleSyncRequest(SyncRequest("node-1", localTime = 1_699_999_999_000L))

    assertThat(result).isEqualTo(serverTime)
  }

  @Test
  fun `completeSync computes correct offset when clocks are perfectly synchronized`() {
    val serverT1 = 1_000L
    val serverT2 = 1_010L
    val clientT0 = 1_000L
    val clientT3 = 1_020L

    every { timeService.getCurrentTime() }
      .returnsMany(serverT1, serverT2)

    syncService.handleSyncRequest(SyncRequest("node-1", localTime = clientT0))
    val response = syncService.completeSync("node-1", clientT0, clientT3)

    // RTT = (clientT3 - clientT0) - (serverT2 - serverT1)
    //     = (1020 - 1000) - (1010 - 1000) = 20 - 10 = 10
    assertThat(response.roundTripTime).isEqualTo(10L)

    // serverMidpoint = (1000 + 1010) / 2 = 1005
    // clientMidpoint = (1000 + 1020) / 2 = 1010
    // offset = serverMidpoint - clientMidpoint - RTT/2 = 1005 - 1010 - 5 = -10
    assertThat(response.offset).isEqualTo(-10L)

    // serverTime = serverMidpoint
    assertThat(response.serverTime).isEqualTo(1005L)
  }

  @Test
  fun `completeSync computes positive offset when server is ahead of client`() {
    // Client thinks it is at T=1000; server is 500ms ahead (T=1500)
    val serverT1 = 1_500L
    val serverT2 = 1_520L
    val clientT0 = 1_000L
    val clientT3 = 1_040L

    every { timeService.getCurrentTime() }.returnsMany(serverT1, serverT2)

    syncService.handleSyncRequest(SyncRequest("node-2", localTime = clientT0))
    val response = syncService.completeSync("node-2", clientT0, clientT3)

    // RTT = (1040 - 1000) - (1520 - 1500) = 40 - 20 = 20
    assertThat(response.roundTripTime).isEqualTo(20L)

    // serverMidpoint = (1500 + 1520) / 2 = 1510
    // clientMidpoint = (1000 + 1040) / 2 = 1020
    // offset = 1510 - 1020 - 10 = 480  (server is ~480ms ahead)
    assertThat(response.offset).isEqualTo(480L)
  }

  @Test
  fun `completeSync returns zero offset when no prior sync request was made for the node`() {
    val fallbackServerTime = 9_999L
    every { timeService.getCurrentTime() } returns fallbackServerTime

    val response = syncService.completeSync("unknown-node", 1_000L, 1_050L)

    assertThat(response.offset).isEqualTo(0L)
    assertThat(response.roundTripTime).isEqualTo(0L)
    assertThat(response.serverTime).isEqualTo(fallbackServerTime)
  }

  @Test
  fun `completeSync removes node state after completion (no second sync without new request)`() {
    val serverT1 = 2_000L
    val serverT2 = 2_010L
    val fallback = 9_000L

    every { timeService.getCurrentTime() }
      .returnsMany(serverT1, serverT2, fallback)

    syncService.handleSyncRequest(SyncRequest("node-3", localTime = 2_000L))
    syncService.completeSync("node-3", 2_000L, 2_020L)

    val second = syncService.completeSync("node-3", 2_000L, 2_020L)

    assertThat(second.offset).isEqualTo(0L)
    assertThat(second.roundTripTime).isEqualTo(0L)
  }

  @Test
  fun `handleSyncRequest supports multiple concurrent nodes independently`() {
    val serverTimeNode1 = 5_000L
    val serverTimeNode2 = 6_000L

    every { timeService.getCurrentTime() }
      .returnsMany(serverTimeNode1, serverTimeNode2, 5_010L, 6_010L)

    syncService.handleSyncRequest(SyncRequest("node-A", localTime = 5_000L))
    syncService.handleSyncRequest(SyncRequest("node-B", localTime = 6_000L))

    val responseA = syncService.completeSync("node-A", 5_000L, 5_020L)
    val responseB = syncService.completeSync("node-B", 6_000L, 6_020L)

    assertThat(responseA.serverTime).isNotEqualTo(responseB.serverTime)
  }
}
