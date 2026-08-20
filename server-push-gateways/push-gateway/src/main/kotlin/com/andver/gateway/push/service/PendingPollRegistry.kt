package com.andver.gateway.push.service

import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.push.model.PushEvent
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface PendingPollRegistry {
  fun registerMono(clientId: String): Mono<List<PushEvent>>
  fun completeLocalWaiters(clientId: String, event: PushEvent): Int
  fun drainAll(): Int
  fun hasWaiters(clientId: String): Boolean
  fun pendingCount(): Int
}

@Component
class DefaultPendingPollRegistry(
  private val metrics: DeliveryMetrics,
) : PendingPollRegistry {
  private val waiters =
    ConcurrentHashMap<String, CopyOnWriteArrayList<Sinks.One<List<PushEvent>>>>()

  override fun registerMono(clientId: String): Mono<List<PushEvent>> {
    val sink = Sinks.one<List<PushEvent>>()
    waiters.computeIfAbsent(clientId) { CopyOnWriteArrayList() }.add(sink)
    metrics.incrementPoll()
    return sink.asMono()
      .doFinally { unregister(clientId, sink) }
  }

  private fun unregister(clientId: String, sink: Sinks.One<List<PushEvent>>) {
    waiters.computeIfPresent(clientId) { _, list ->
      if (list.remove(sink)) {
        metrics.decrementPoll()
      }
      if (list.isEmpty()) null else list
    }
  }

  override fun completeLocalWaiters(clientId: String, event: PushEvent): Int {
    val list = waiters.remove(clientId) ?: return 0
    var woken = 0
    list.forEach { sink ->
      val result = sink.tryEmitValue(listOf(event))
      if (result.isSuccess) {
        woken++
      }
      metrics.decrementPoll()
    }
    if (woken > 0) {
      metrics.recordWaitersWoken(woken)
    }
    return woken
  }

  override fun drainAll(): Int {
    var drained = 0
    waiters.keys.toList().forEach { clientId ->
      val list = waiters.remove(clientId) ?: return@forEach
      list.forEach { sink ->
        sink.tryEmitError(
          ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gateway draining"),
        )
        metrics.decrementPoll()
        drained++
      }
    }
    return drained
  }

  override fun hasWaiters(clientId: String): Boolean = !waiters[clientId].isNullOrEmpty()

  override fun pendingCount(): Int = metrics.pollCount()
}
