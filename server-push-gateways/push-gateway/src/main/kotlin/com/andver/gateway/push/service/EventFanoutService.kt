package com.andver.gateway.push.service

import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.push.model.PushEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.Disposable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface EventFanoutService {
  fun ensureSubscribed(clientId: String)
  fun maybeUnsubscribe(clientId: String)
  fun drainSubscriptions()
}

@Service
class DefaultEventFanoutService(
  private val clientChannelService: ClientChannelService,
  private val sseConnectionManager: SseConnectionManager,
  private val pendingPollRegistry: PendingPollRegistry,
  private val metrics: DeliveryMetrics,
) : EventFanoutService {
  private val log = LoggerFactory.getLogger(DefaultEventFanoutService::class.java)
  private val subscriptions = ConcurrentHashMap<String, Disposable>()

  override fun ensureSubscribed(clientId: String) {
    subscriptions.computeIfAbsent(clientId) {
      clientChannelService.subscribe(clientId)
        .subscribe(
          { event -> onEvent(event) },
          { error ->
            log.warn("Redis subscription error for clientId={}: {}", clientId, error.message)
            subscriptions.remove(clientId)
          },
          { subscriptions.remove(clientId) },
        )
    }
  }

  override fun maybeUnsubscribe(clientId: String) {
    if (!sseConnectionManager.hasSessions(clientId) && !pendingPollRegistry.hasWaiters(clientId)) {
      subscriptions.remove(clientId)?.dispose()
    }
  }

  override fun drainSubscriptions() {
    subscriptions.keys.toList().forEach { clientId ->
      subscriptions.remove(clientId)?.dispose()
    }
  }

  private fun onEvent(event: PushEvent) {
    val sseDelivered = sseConnectionManager.deliver(event.clientId, event)
    if (sseDelivered > 0) {
      metrics.recordDelivered("sse")
      recordLatency("sse", event)
    }
    val pollWoken = pendingPollRegistry.completeLocalWaiters(event.clientId, event)
    if (pollWoken > 0) {
      metrics.recordDelivered("poll")
      recordLatency("poll", event)
    }
  }

  private fun recordLatency(transport: String, event: PushEvent) {
    val latency = Duration.between(event.publishedAt, Instant.now())
    if (!latency.isNegative) {
      metrics.recordDeliveryLatency(transport, latency)
    }
  }
}
