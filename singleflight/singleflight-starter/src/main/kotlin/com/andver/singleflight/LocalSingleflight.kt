package com.andver.singleflight

import com.andver.singleflight.internal.FlightCodec
import com.andver.singleflight.internal.FlightEnvelope
import com.andver.singleflight.internal.SingleflightSharedException
import com.andver.singleflight.metrics.SingleflightMetrics
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

class LocalSingleflight(
  private val properties: SingleflightProperties,
  private val metrics: SingleflightMetrics,
  private val codec: FlightCodec,
) : Singleflight {

  private val inflight = ConcurrentHashMap<String, Sinks.One<FlightEnvelope>>()

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
    while (true) {
      val existing = inflight[key]
      if (existing != null) {
        return joinWaiter(key, existing, type, loader)
      }

      val sink = Sinks.one<FlightEnvelope>()
      val raced = inflight.putIfAbsent(key, sink)
      if (raced != null) {
        continue
      }

      metrics.leaderStart(MODE)
      metrics.upstreamCall(MODE)

      return loader()
        .map { value ->
          val envelope = codec.encodeSuccess(value)
          sink.tryEmitValue(envelope)
          SingleflightOutcome(value, SingleflightRole.LEADER)
        }
        .onErrorResume { error ->
          if (properties.shareErrors) {
            metrics.sharedError(MODE)
            sink.tryEmitValue(codec.encodeError(error))
          } else {
            sink.tryEmitValue(FlightEnvelope.retry())
          }
          Mono.error(error)
        }
        .doFinally {
          inflight.remove(key, sink)
        }
    }
  }

  private fun <T : Any> joinWaiter(
    key: String,
    sink: Sinks.One<FlightEnvelope>,
    type: Class<T>,
    loader: () -> Mono<T>,
  ): Mono<SingleflightOutcome<T>> {
    metrics.waiterJoin(MODE)
    return sink.asMono()
      .timeout(properties.waiterTimeout)
      .onErrorMap(TimeoutException::class.java) { error ->
        metrics.waiterTimeout(MODE)
        error
      }
      .flatMap { envelope ->
        when {
          envelope.retry -> executeAttempt(key, type, loader)
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
  }

  companion object {
    private const val MODE = "local"
  }
}
