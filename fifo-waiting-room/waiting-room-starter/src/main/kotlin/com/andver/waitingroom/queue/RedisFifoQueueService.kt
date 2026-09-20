package com.andver.waitingroom.queue

import com.andver.waitingroom.WaitingRoomProperties
import com.andver.waitingroom.metrics.WaitingRoomMetrics
import com.andver.waitingroom.model.EventMeta
import com.andver.waitingroom.model.JoinOutcome
import com.andver.waitingroom.model.QueueSnapshot
import com.andver.waitingroom.model.RejectReason
import com.andver.waitingroom.model.VisitorStatus
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scripting.support.ResourceScriptSource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class RedisFifoQueueService(
  private val redis: ReactiveStringRedisTemplate,
  private val properties: WaitingRoomProperties,
  private val metrics: WaitingRoomMetrics,
) : FifoQueueService {

  private val joinScript = listScript("lua/join.lua")
  private val leaveScript = longScript("lua/leave.lua")
  private val admitPopScript = listScript("lua/admit_pop.lua")
  private val markAdmittedScript = stringScript("lua/mark_admitted.lua")

  override fun join(eventId: String, visitorId: String): Mono<JoinOutcome> {
    val now = Instant.now().toEpochMilli().toString()
    val keys = listOf(
      RedisKeys.queue(eventId),
      RedisKeys.seq(eventId),
      RedisKeys.visitor(eventId, visitorId),
      RedisKeys.meta(eventId),
      RedisKeys.EVENTS,
    )
    return redis.execute(
      joinScript,
      keys,
      listOf(
        visitorId,
        now,
        properties.maxQueue.toString(),
        "1",
        eventId,
      ),
    ).next().map { raw -> parseJoin(eventId, visitorId, raw) }
      .flatMap { outcome ->
        when (outcome) {
          is JoinOutcome.Ok -> refreshDepth(eventId)
            .then(getMeta(eventId))
            .map { meta ->
              metrics.joinOk(eventId)
              JoinOutcome.Ok(
                outcome.snapshot.copy(etaSeconds = eta(outcome.snapshot.position, meta.admitRate)),
              )
            }
          is JoinOutcome.Rejected -> {
            metrics.joinRejected(eventId, outcome.reason)
            Mono.just(outcome)
          }
        }
      }
  }

  override fun status(eventId: String, visitorId: String): Mono<QueueSnapshot> {
    val visitorKey = RedisKeys.visitor(eventId, visitorId)
    return redis.opsForHash<String, String>().entries(visitorKey)
      .collectMap({ it.key }, { it.value })
      .flatMap { fields ->
        if (fields.isEmpty()) {
          return@flatMap Mono.just(
            QueueSnapshot(eventId, visitorId, -1, VisitorStatus.UNKNOWN),
          )
        }
        val status = VisitorStatus.valueOf(fields["status"] ?: "UNKNOWN")
        val ticket = fields["ticketId"]?.takeIf { it.isNotBlank() }
        val joinedAt = fields["joinedAt"]?.toLongOrNull()
        when (status) {
          VisitorStatus.WAITING -> redis.opsForZSet()
            .rank(RedisKeys.queue(eventId), visitorId)
            .map { rank ->
              QueueSnapshot(
                eventId = eventId,
                visitorId = visitorId,
                position = rank + 1,
                status = status,
                ticket = null,
                joinedAtEpochMs = joinedAt,
              )
            }
            .defaultIfEmpty(
              QueueSnapshot(eventId, visitorId, -1, VisitorStatus.UNKNOWN, joinedAtEpochMs = joinedAt),
            )
            .flatMap { snap -> enrichEtaMono(snap) }
          else -> Mono.just(
            QueueSnapshot(
              eventId = eventId,
              visitorId = visitorId,
              position = 0,
              status = status,
              ticket = ticket,
              joinedAtEpochMs = joinedAt,
              etaSeconds = 0,
            ),
          )
        }
      }
  }

  override fun leave(eventId: String, visitorId: String): Mono<Boolean> {
    return redis.execute(
      leaveScript,
      listOf(RedisKeys.queue(eventId), RedisKeys.visitor(eventId, visitorId)),
      listOf(visitorId),
    ).next()
      .map { it > 0 }
      .flatMap { removed ->
        metrics.leave(eventId)
        refreshDepth(eventId).thenReturn(removed)
      }
  }

  override fun updateMeta(meta: EventMeta): Mono<EventMeta> {
    val key = RedisKeys.meta(meta.eventId)
    return redis.opsForHash<String, String>().putAll(
      key,
      mapOf(
        "admitRate" to meta.admitRate.toString(),
        "maxQueue" to meta.maxQueue.toString(),
        "open" to if (meta.open) "1" else "0",
      ),
    ).then(redis.opsForSet().add(RedisKeys.EVENTS, meta.eventId))
      .thenReturn(meta)
  }

  override fun getMeta(eventId: String): Mono<EventMeta> {
    return redis.opsForHash<String, String>().entries(RedisKeys.meta(eventId))
      .collectMap({ it.key }, { it.value })
      .map { fields ->
        EventMeta(
          eventId = eventId,
          admitRate = fields["admitRate"]?.toIntOrNull() ?: properties.defaultAdmitRate,
          maxQueue = fields["maxQueue"]?.toIntOrNull() ?: properties.maxQueue,
          open = fields["open"]?.let { it != "0" } ?: true,
        )
      }
  }

  override fun queueDepth(eventId: String): Mono<Long> =
    redis.opsForZSet().size(RedisKeys.queue(eventId)).defaultIfEmpty(0)

  override fun listEventIds(): Flux<String> =
    redis.opsForSet().members(RedisKeys.EVENTS)
      .switchIfEmpty(Flux.fromIterable(properties.defaultEventIds))

  override fun popAdmitted(eventId: String, limit: Int): Flux<String> {
    if (limit < 1) return Flux.empty()
    return redis.execute(admitPopScript, listOf(RedisKeys.queue(eventId)), listOf(limit.toString()))
      .flatMapIterable { list -> list.map { it.toString() } }
  }

  override fun markAdmitted(eventId: String, visitorId: String, ticketId: String): Mono<Long> {
    return redis.execute(
      markAdmittedScript,
      listOf(RedisKeys.visitor(eventId, visitorId)),
      listOf(ticketId),
    ).next()
      .map { raw -> raw.takeIf { it.isNotBlank() }?.toLongOrNull() ?: 0L }
      .defaultIfEmpty(0L)
  }

  override fun tryAcquireAdmitLock(eventId: String): Mono<Boolean> =
    redis.opsForValue()
      .setIfAbsent(RedisKeys.admitLock(eventId), "1", properties.lockTtl)
      .defaultIfEmpty(false)

  override fun releaseAdmitLock(eventId: String): Mono<Boolean> =
    redis.delete(RedisKeys.admitLock(eventId)).map { it > 0 }

  private fun refreshDepth(eventId: String): Mono<Void> =
    queueDepth(eventId)
      .doOnNext { depth -> metrics.setQueueDepth(eventId, depth) }
      .then()

  private fun enrichEtaMono(snapshot: QueueSnapshot): Mono<QueueSnapshot> =
    getMeta(snapshot.eventId).map { meta ->
      snapshot.copy(etaSeconds = eta(snapshot.position, meta.admitRate))
    }

  private fun eta(position: Long, admitRate: Int): Long {
    if (position <= 0 || admitRate <= 0) return 0
    return (position + admitRate - 1) / admitRate
  }

  private fun parseJoin(eventId: String, visitorId: String, raw: List<*>): JoinOutcome {
    val ok = raw[0].toString().toLong()
    if (ok == 0L) {
      val reason = when (raw[1].toString()) {
        "FULL" -> RejectReason.QUEUE_FULL
        else -> RejectReason.EVENT_CLOSED
      }
      return JoinOutcome.Rejected(reason)
    }
    val position = raw[1].toString().toLong()
    val status = VisitorStatus.valueOf(raw[2].toString())
    val ticket = raw.getOrNull(3)?.toString()?.takeIf { it.isNotBlank() }
    val joinedAt = raw.getOrNull(4)?.toString()?.toLongOrNull()
    return JoinOutcome.Ok(
      QueueSnapshot(
        eventId = eventId,
        visitorId = visitorId,
        position = position,
        status = status,
        ticket = ticket,
        joinedAtEpochMs = joinedAt,
      ),
    )
  }

  private fun listScript(path: String): DefaultRedisScript<List<*>> =
    DefaultRedisScript<List<*>>().apply {
      setScriptSource(ResourceScriptSource(ClassPathResource(path)))
      setResultType(List::class.java)
    }

  private fun longScript(path: String): DefaultRedisScript<Long> =
    DefaultRedisScript<Long>().apply {
      setScriptSource(ResourceScriptSource(ClassPathResource(path)))
      setResultType(Long::class.java)
    }

  private fun stringScript(path: String): DefaultRedisScript<String> =
    DefaultRedisScript<String>().apply {
      setScriptSource(ResourceScriptSource(ClassPathResource(path)))
      setResultType(String::class.java)
    }
}
