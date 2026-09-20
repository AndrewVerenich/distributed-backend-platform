package com.andver.waitingroom.metrics

import com.andver.waitingroom.model.RejectReason
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class MicrometerWaitingRoomMetrics(
  private val registry: MeterRegistry,
) : WaitingRoomMetrics {

  private val depths = ConcurrentHashMap<String, AtomicLong>()

  override fun joinOk(eventId: String) {
    registry.counter("wr_join", "eventId", eventId).increment()
  }

  override fun joinRejected(eventId: String, reason: RejectReason) {
    registry.counter("wr_join_rejected", "eventId", eventId, "reason", reason.name).increment()
  }

  override fun leave(eventId: String) {
    registry.counter("wr_leave", "eventId", eventId).increment()
  }

  override fun admit(eventId: String, count: Int) {
    if (count > 0) {
      registry.counter("wr_admit", "eventId", eventId).increment(count.toDouble())
    }
  }

  override fun ticketIssued(eventId: String) {
    registry.counter("wr_ticket_issued", "eventId", eventId).increment()
  }

  override fun ticketReplayRejected() {
    registry.counter("wr_ticket_replay_rejected").increment()
  }

  override fun ticketConsumed() {
    registry.counter("wr_ticket_consumed").increment()
  }

  override fun recordWait(eventId: String, wait: Duration) {
    Timer.builder("wr_wait")
      .tag("eventId", eventId)
      .publishPercentileHistogram()
      .register(registry)
      .record(wait)
  }

  override fun setQueueDepth(eventId: String, depth: Long) {
    val gauge = depths.computeIfAbsent(eventId) { id ->
      val value = AtomicLong(0)
      registry.gauge("wr_queue_depth", listOf(io.micrometer.core.instrument.Tag.of("eventId", id)), value)
      value
    }
    gauge.set(depth)
  }
}
