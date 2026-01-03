package com.andver.client.notification

import com.andver.client.notification.handler.ClientNotificationEventHandler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.EnableKafka

@AutoConfiguration
@EnableKafka
@ConditionalOnProperty(prefix = "client.notification.consumer", name = ["enabled"], matchIfMissing = false)
class ClientNotificationConsumerAutoConfiguration(
  private val eventHandlers: List<ClientNotificationEventHandler>,
) {
  @Bean
  fun clientNotificationConsumer(): ClientNotificationConsumer {
    return ClientNotificationConsumer(eventHandlers)
  }
}