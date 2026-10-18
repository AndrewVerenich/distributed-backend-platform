package com.andver.singleflight

import com.andver.singleflight.internal.FlightCodec
import com.andver.singleflight.metrics.MicrometerSingleflightMetrics
import com.andver.singleflight.metrics.NoopSingleflightMetrics
import com.andver.singleflight.metrics.SingleflightMetrics
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnProperty(prefix = "singleflight", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SingleflightProperties::class)
class SingleflightAutoConfiguration {

  private val log = LoggerFactory.getLogger(SingleflightAutoConfiguration::class.java)

  @Bean
  @ConditionalOnMissingBean
  fun singleflightMetrics(meterRegistry: ObjectProvider<MeterRegistry>): SingleflightMetrics {
    val registry = meterRegistry.ifAvailable
    return if (registry != null) MicrometerSingleflightMetrics(registry) else NoopSingleflightMetrics
  }

  @Bean
  @ConditionalOnMissingBean
  fun flightCodec(objectMapper: ObjectProvider<ObjectMapper>): FlightCodec {
    val mapper = objectMapper.ifAvailable ?: ObjectMapper().findAndRegisterModules()
    return FlightCodec(mapper)
  }

  @Bean
  @ConditionalOnMissingBean
  fun singleflight(
    properties: SingleflightProperties,
    metrics: SingleflightMetrics,
    codec: FlightCodec,
    redis: ObjectProvider<ReactiveStringRedisTemplate>,
  ): Singleflight {
    return when (properties.mode) {
      SingleflightMode.OFF -> {
        log.info("singleflight mode=off (passthrough)")
        PassthroughSingleflight(metrics)
      }
      SingleflightMode.LOCAL -> {
        log.info(
          "singleflight mode=local shareErrors={} waiterTimeout={}",
          properties.shareErrors,
          properties.waiterTimeout,
        )
        LocalSingleflight(properties, metrics, codec)
      }
      SingleflightMode.DISTRIBUTED -> {
        val template = redis.ifAvailable
          ?: error("singleflight.mode=distributed requires ReactiveStringRedisTemplate")
        log.info(
          "singleflight mode=distributed namespace={} lease={} shareErrors={}",
          properties.namespace,
          properties.distributed.lease,
          properties.shareErrors,
        )
        DistributedSingleflight(template, properties, metrics, codec)
      }
    }
  }
}
