package com.andver.singleflight

import com.andver.singleflight.internal.FlightCodec
import com.andver.singleflight.internal.FlightEnvelope
import com.andver.singleflight.internal.SingleflightSharedException
import com.andver.singleflight.metrics.SingleflightMetrics
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

/**
 * Cross-process coalescing via Redis leader lease + result key.
 *
 * Leader: SET lock NX PX → run loader → SET result → DEL lock.
 * Waiter: poll result key; if lock gone and no result → failover (re-elect).
 */
class DistributedSingleflight(
  private val redis: ReactiveStringRedisTemplate,
  private val properties: SingleflightProperties,
  private val metrics: SingleflightMetrics,
  private val codec: FlightCodec,
) : Singleflight {

  override fun <T : Any> execute(
    key: String,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> = executeAttempt(key, type, loader)

  private fun <T : Any> executeAttempt(
    key: String,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> {
    val lockKey = lockKey(key)
    val resultKey = resultKey(key)
    val token = UUID.randomUUID().toString()

    return redis.opsForValue()
      .setIfAbsent(lockKey, token, properties.distributed.lease)
      .defaultIfEmpty(false)
      .flatMap { acquired ->
        if (acquired) {
          runAsLeader(lockKey, resultKey, token, loader)
        } else {
          joinAsWaiter(key, lockKey, resultKey, type, loader)
        }
      }
  }

  private fun <T : Any> runAsLeader(
    lockKey: String,
    resultKey: String,
    token: String,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> {
    metrics.leaderStart(MODE)
    metrics.upstreamCall(MODE)

    return loader()
      .flatMap { value ->
        val envelope = codec.encodeSuccess(value)
        publishResult(resultKey, envelope)
          .then(releaseLock(lockKey, token))
          .thenReturn(SingleflightOutcome(value, SingleflightRole.LEADER))
      }
      .onErrorResume { error ->
        val envelope = if (properties.shareErrors) {
          metrics.sharedError(MODE)
          codec.encodeError(error)
        } else {
          FlightEnvelope.retry()
        }
        publishResult(resultKey, envelope)
          .then(releaseLock(lockKey, token))
          .then(Mono.error(error))
      }
  }

  private fun <T : Any> joinAsWaiter(
    key: String,
    lockKey: String,
    resultKey: String,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> {
    metrics.waiterJoin(MODE)
    val deadline = System.nanoTime() + properties.waiterTimeout.toNanos()

    return pollUntilResultOrFailover(lockKey, resultKey, deadline)
      .flatMap { envelope ->
        when {
          envelope == null -> {
            metrics.leaderFailover(MODE)
            executeAttempt(key, type, loader)
          }
          envelope.retry -> {
            metrics.leaderFailover(MODE)
            executeAttempt(key, type, loader)
          }
          envelope.ok -> Mono.just(
            SingleflightOutcome(codec.decode(envelope, type), SingleflightRole.WAITER),
          )
          else -> Mono.error(
            SingleflightSharedException(
              envelope.errorMessage ?: "shared error",
              envelope.errorClass ?: "java.lang.RuntimeException",
            ),
          )
        }
      }
      .onErrorMap(TimeoutException::class.java) { error ->
        metrics.waiterTimeout(MODE)
        error
      }
  }

  /**
   * @return envelope when published; null when lock disappeared without a result (failover).
   */
  private fun pollUntilResultOrFailover(
    lockKey: String,
    resultKey: String,
    deadlineNanos: Long,
  ): Mono<FlightEnvelope?> =
    Mono.defer<FlightEnvelope?> {
      val left = remaining(deadlineNanos)
      if (left.isZero || left.isNegative) {
        return@defer Mono.error(TimeoutException("singleflight waiter timeout"))
      }

      redis.opsForValue().get(resultKey)
        .map { json -> codec.read(json) }
        .flatMap<FlightEnvelope?> { envelope -> Mono.just(envelope) }
        .switchIfEmpty(
          Mono.defer {
            redis.hasKey(lockKey).flatMap { lockExists ->
              if (!lockExists) {
                Mono.justOrEmpty<FlightEnvelope>(null)
              } else {
                Mono.delay(minOf(properties.distributed.pollInterval, left))
                  .then(pollUntilResultOrFailover(lockKey, resultKey, deadlineNanos))
              }
            }
          },
        )
    }

  private fun remaining(deadlineNanos: Long): Duration {
    val leftNanos = deadlineNanos - System.nanoTime()
    return if (leftNanos <= 0) Duration.ZERO else Duration.ofNanos(leftNanos)
  }

  private fun publishResult(resultKey: String, envelope: FlightEnvelope): Mono<Boolean> =
    redis.opsForValue().set(resultKey, codec.write(envelope), properties.distributed.resultTtl)

  private fun releaseLock(lockKey: String, token: String): Mono<Boolean> =
    redis.opsForValue().get(lockKey)
      .flatMap { current ->
        if (current == token) {
          redis.delete(lockKey).map { it > 0 }
        } else {
          Mono.just(false)
        }
      }
      .defaultIfEmpty(false)

  private fun lockKey(key: String) = "sf:lock:${properties.namespace}:$key"

  private fun resultKey(key: String) = "sf:result:${properties.namespace}:$key"

  companion object {
    private const val MODE = "distributed"
  }
}
