package com.andver.waitingroom

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "waiting-room")
data class WaitingRoomProperties(
  /** Opaque ticket TTL after admission. */
  val ticketTtl: Duration = Duration.ofMinutes(2),
  /** Default admissions per second when event meta is missing. */
  val defaultAdmitRate: Int = 50,
  /** Default max queue size when event meta is missing. */
  val maxQueue: Int = 10_000,
  /** Admit-worker lock lease. */
  val lockTtl: Duration = Duration.ofSeconds(2),
  /** Events to poll for admission when none were registered yet. */
  val defaultEventIds: List<String> = listOf("flash-sale-1"),
  val ticketFilter: TicketFilterProperties = TicketFilterProperties(),
) {
  data class TicketFilterProperties(
    /** Enable WebFilter that consumes opaque tickets on protected routes. */
    val enabled: Boolean = false,
    /** Path prefixes protected by the ticket filter (Ant-style prefix match). */
    val pathPrefixes: List<String> = listOf("/checkout"),
  )
}
