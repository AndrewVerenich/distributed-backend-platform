package com.andver.waitingroom.model

data class QueueSnapshot(
  val eventId: String,
  val visitorId: String,
  val position: Long,
  val status: VisitorStatus,
  val ticket: String? = null,
  val etaSeconds: Long? = null,
  val joinedAtEpochMs: Long? = null,
)

sealed class JoinOutcome {
  data class Ok(val snapshot: QueueSnapshot) : JoinOutcome()
  data class Rejected(val reason: RejectReason) : JoinOutcome()
}

enum class RejectReason {
  QUEUE_FULL,
  EVENT_CLOSED,
}

data class EventMeta(
  val eventId: String,
  val admitRate: Int,
  val maxQueue: Int,
  val open: Boolean,
)

data class TicketPayload(
  val ticketId: String,
  val eventId: String,
  val visitorId: String,
) {
  fun encode(): String = "$eventId$SEPARATOR$visitorId"

  companion object {
    private const val SEPARATOR = "|"

    fun decode(ticketId: String, raw: String): TicketPayload {
      val parts = raw.split(SEPARATOR, limit = 2)
      require(parts.size == 2) { "Invalid ticket payload: $raw" }
      return TicketPayload(ticketId = ticketId, eventId = parts[0], visitorId = parts[1])
    }
  }
}
