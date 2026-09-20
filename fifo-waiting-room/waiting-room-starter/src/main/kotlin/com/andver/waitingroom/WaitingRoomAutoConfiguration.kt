package com.andver.waitingroom

import com.andver.waitingroom.filter.TicketWebFilter
import com.andver.waitingroom.metrics.MicrometerWaitingRoomMetrics
import com.andver.waitingroom.metrics.WaitingRoomMetrics
import com.andver.waitingroom.queue.AdmissionService
import com.andver.waitingroom.queue.FifoQueueService
import com.andver.waitingroom.queue.RedisFifoQueueService
import com.andver.waitingroom.ticket.RedisTicketService
import com.andver.waitingroom.ticket.TicketService
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.web.server.WebFilter

/**
 * Must run after Redis reactive auto-config: class-level [ConditionalOnBean] on
 * ReactiveStringRedisTemplate is evaluated too early otherwise and skips all beans.
 */
@AutoConfiguration(after = [RedisReactiveAutoConfiguration::class])
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
@ConditionalOnBean(ReactiveStringRedisTemplate::class)
@EnableConfigurationProperties(WaitingRoomProperties::class)
class WaitingRoomAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  fun waitingRoomMetrics(meterRegistry: MeterRegistry): WaitingRoomMetrics =
    MicrometerWaitingRoomMetrics(meterRegistry)

  @Bean
  @ConditionalOnMissingBean
  fun fifoQueueService(
    redis: ReactiveStringRedisTemplate,
    properties: WaitingRoomProperties,
    metrics: WaitingRoomMetrics,
  ): FifoQueueService = RedisFifoQueueService(redis, properties, metrics)

  @Bean
  @ConditionalOnMissingBean
  fun ticketService(
    redis: ReactiveStringRedisTemplate,
    properties: WaitingRoomProperties,
    metrics: WaitingRoomMetrics,
  ): TicketService = RedisTicketService(redis, properties, metrics)

  @Bean
  @ConditionalOnMissingBean
  fun admissionService(
    queue: FifoQueueService,
    tickets: TicketService,
    metrics: WaitingRoomMetrics,
  ): AdmissionService = AdmissionService(queue, tickets, metrics)

  @Bean
  @ConditionalOnProperty(prefix = "waiting-room.ticket-filter", name = ["enabled"], havingValue = "true")
  fun ticketWebFilter(
    ticketService: TicketService,
    properties: WaitingRoomProperties,
  ): WebFilter = TicketWebFilter(ticketService, properties)
}
