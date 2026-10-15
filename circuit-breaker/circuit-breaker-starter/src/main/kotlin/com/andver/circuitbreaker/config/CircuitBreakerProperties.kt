package com.andver.circuitbreaker.config

import com.andver.circuitbreaker.core.CircuitBreakerConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "circuit-breaker")
data class CircuitBreakerProperties(
  val enabled: Boolean = true,
  val configs: Map<String, InstanceConfig> = mapOf("default" to InstanceConfig()),
  val instances: Map<String, InstanceRef> = emptyMap(),
) {
  data class InstanceConfig(
    val slidingWindowSize: Int = 10,
    val minimumNumberOfCalls: Int = 5,
    val failureRateThreshold: Float = 50f,
    val slowCallRateThreshold: Float = 50f,
    val slowCallDuration: Duration = Duration.ofSeconds(2),
    val waitDurationInOpenState: Duration = Duration.ofSeconds(5),
    val permittedNumberOfCallsInHalfOpenState: Int = 3,
  ) {
    fun toConfig(): CircuitBreakerConfig =
      CircuitBreakerConfig(
        slidingWindowSize = slidingWindowSize,
        minimumNumberOfCalls = minimumNumberOfCalls,
        failureRateThreshold = failureRateThreshold,
        slowCallRateThreshold = slowCallRateThreshold,
        slowCallDuration = slowCallDuration,
        waitDurationInOpenState = waitDurationInOpenState,
        permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState,
      )
  }

  data class InstanceRef(
    val baseConfig: String = "default",
    val slidingWindowSize: Int? = null,
    val minimumNumberOfCalls: Int? = null,
    val failureRateThreshold: Float? = null,
    val slowCallRateThreshold: Float? = null,
    val slowCallDuration: Duration? = null,
    val waitDurationInOpenState: Duration? = null,
    val permittedNumberOfCallsInHalfOpenState: Int? = null,
  )

  fun resolveConfigs(): Map<String, CircuitBreakerConfig> {
    val bases = configs.mapValues { it.value.toConfig() }
    val defaultConfig = bases["default"] ?: CircuitBreakerConfig()
    if (instances.isEmpty()) {
      return emptyMap()
    }
    return instances.mapValues { (_, ref) ->
      val base = bases[ref.baseConfig] ?: defaultConfig
      CircuitBreakerConfig(
        slidingWindowSize = ref.slidingWindowSize ?: base.slidingWindowSize,
        minimumNumberOfCalls = ref.minimumNumberOfCalls ?: base.minimumNumberOfCalls,
        failureRateThreshold = ref.failureRateThreshold ?: base.failureRateThreshold,
        slowCallRateThreshold = ref.slowCallRateThreshold ?: base.slowCallRateThreshold,
        slowCallDuration = ref.slowCallDuration ?: base.slowCallDuration,
        waitDurationInOpenState = ref.waitDurationInOpenState ?: base.waitDurationInOpenState,
        permittedNumberOfCallsInHalfOpenState =
          ref.permittedNumberOfCallsInHalfOpenState
            ?: base.permittedNumberOfCallsInHalfOpenState,
      )
    }
  }

  fun defaultConfig(): CircuitBreakerConfig =
    configs["default"]?.toConfig() ?: CircuitBreakerConfig()
}
