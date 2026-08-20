package com.andver.gateway.push.service

import com.andver.gateway.push.metrics.DeliveryMetrics
import com.andver.push.model.PushEvent
import org.springframework.stereotype.Component
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class SseSession(
  val clientId: String,
  val sink: Sinks.Many<PushEvent>,
  val closed: AtomicBoolean = AtomicBoolean(false),
)

interface SseConnectionManager {
  fun register(clientId: String): SseSession
  fun unregister(session: SseSession)
  fun deliver(clientId: String, event: PushEvent): Int
  fun drainAll(): Int
  fun hasSessions(clientId: String): Boolean
  fun activeCount(): Int
}

@Component
class DefaultSseConnectionManager(
  private val metrics: DeliveryMetrics,
) : SseConnectionManager {
  private val sessions = ConcurrentHashMap<String, MutableList<SseSession>>()

  override fun register(clientId: String): SseSession {
    val sink = Sinks.many().multicast().onBackpressureBuffer<PushEvent>(256, false)
    val session = SseSession(clientId = clientId, sink = sink)
    sessions.compute(clientId) { _, existing ->
      val list = existing ?: mutableListOf()
      synchronized(list) { list.add(session) }
      list
    }
    metrics.incrementSse()
    return session
  }

  override fun unregister(session: SseSession) {
    if (!session.closed.compareAndSet(false, true)) {
      return
    }
    sessions.computeIfPresent(session.clientId) { _, list ->
      synchronized(list) { list.remove(session) }
      if (list.isEmpty()) null else list
    }
    session.sink.tryEmitComplete()
    metrics.decrementSse()
  }

  override fun deliver(clientId: String, event: PushEvent): Int {
    val list = sessions[clientId] ?: return 0
    var delivered = 0
    synchronized(list) {
      list.forEach { session ->
        if (!session.closed.get()) {
          val result = session.sink.tryEmitNext(event)
          if (result.isSuccess) {
            delivered++
          }
        }
      }
    }
    return delivered
  }

  override fun drainAll(): Int {
    var drained = 0
    sessions.keys.toList().forEach { clientId ->
      val list = sessions.remove(clientId) ?: return@forEach
      synchronized(list) {
        list.forEach { session ->
          if (session.closed.compareAndSet(false, true)) {
            session.sink.tryEmitComplete()
            metrics.decrementSse()
            drained++
          }
        }
      }
    }
    return drained
  }

  override fun hasSessions(clientId: String): Boolean {
    val list = sessions[clientId] ?: return false
    synchronized(list) {
      return list.any { !it.closed.get() }
    }
  }

  override fun activeCount(): Int = metrics.sseCount()
}
