package com.andver.banking.command.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

interface CommandMetrics {
  fun recordCommand(type: String, result: String)
  fun recordAppend(eventType: String, duration: Duration)

  companion object {
    val NOOP: CommandMetrics = object : CommandMetrics {
      override fun recordCommand(type: String, result: String) = Unit
      override fun recordAppend(eventType: String, duration: Duration) = Unit
    }
  }
}

@Component
class MicrometerCommandMetrics(
  private val registry: MeterRegistry,
) : CommandMetrics {

  private val commands = ConcurrentHashMap<String, Counter>()
  private val appendTimers = ConcurrentHashMap<String, Timer>()
  private val appended = ConcurrentHashMap<String, Counter>()

  override fun recordCommand(type: String, result: String) {
    commands.computeIfAbsent("$type/$result") {
      Counter.builder("es_commands_total")
        .tag("type", type)
        .tag("result", result)
        .register(registry)
    }.increment()
  }

  override fun recordAppend(eventType: String, duration: Duration) {
    appended.computeIfAbsent(eventType) {
      Counter.builder("es_events_appended_total")
        .tag("eventType", eventType)
        .register(registry)
    }.increment()
    appendTimers.computeIfAbsent(eventType) {
      Timer.builder("es_command_append_seconds")
        .tag("eventType", eventType)
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
    }.record(duration)
  }
}
