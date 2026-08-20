package com.andver.gateway.push.service

import com.andver.gateway.push.metrics.DeliveryMetrics
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

interface GracefulShutdownHandler : SmartLifecycle

@Component
class DefaultGracefulShutdownHandler(
  private val connectionGate: ConnectionGate,
  private val sseConnectionManager: SseConnectionManager,
  private val pendingPollRegistry: PendingPollRegistry,
  private val eventFanoutService: EventFanoutService,
  private val metrics: DeliveryMetrics,
) : GracefulShutdownHandler {

  private val log = LoggerFactory.getLogger(DefaultGracefulShutdownHandler::class.java)
  private val running = AtomicBoolean(false)

  override fun start() {
    running.set(true)
  }

  override fun stop() {
    val startedAt = System.nanoTime()
    log.info("Graceful shutdown: begin drain")
    connectionGate.beginDrain()
    val drainedSse = sseConnectionManager.drainAll()
    val drainedPoll = pendingPollRegistry.drainAll()
    eventFanoutService.drainSubscriptions()
    val total = drainedSse + drainedPoll
    metrics.recordDrained(total)
    val duration = Duration.ofNanos(System.nanoTime() - startedAt)
    metrics.recordDrainDuration(duration)
    log.info(
      "Graceful shutdown: drained sse={} poll={} in {}ms",
      drainedSse,
      drainedPoll,
      duration.toMillis(),
    )
    running.set(false)
  }

  override fun stop(callback: Runnable) {
    stop()
    callback.run()
  }

  override fun isRunning(): Boolean = running.get()

  override fun getPhase(): Int = Integer.MAX_VALUE - 100
}
