package com.andver.waitingroom.metrics

import com.andver.waitingroom.model.RejectReason
import java.time.Duration

interface WaitingRoomMetrics {
  fun joinOk(eventId: String)
  fun joinRejected(eventId: String, reason: RejectReason)
  fun leave(eventId: String)
  fun admit(eventId: String, count: Int)
  fun ticketIssued(eventId: String)
  fun ticketReplayRejected()
  fun ticketConsumed()
  fun recordWait(eventId: String, wait: Duration)
  fun setQueueDepth(eventId: String, depth: Long)
}
