package com.andver.inbox

import com.andver.inbox.handler.InboxEventHandler
import com.andver.inbox.repository.InboxRepository
import com.andver.inbox.service.DefaultInboxEventProcessor
import com.andver.inbox.service.InboxEventProcessor
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.kafka.annotation.EnableKafka

@AutoConfiguration
@EnableR2dbcRepositories(basePackages = ["com.andver.inbox.repository"])
@EnableKafka
@ConditionalOnProperty(prefix = "inbox.consumer", name = ["enabled"], matchIfMissing = false)
class InboxAutoConfiguration(
  private val inboxRepository: InboxRepository,
  private val eventHandlers: List<InboxEventHandler>,
  private val meterRegistry: ObjectProvider<MeterRegistry>,
) {

  @Bean
  fun inboxEventProcessor(): InboxEventProcessor {
    return DefaultInboxEventProcessor(inboxRepository, meterRegistry.ifAvailable)
  }

  @Bean
  fun inboxEventConsumer(processor: InboxEventProcessor): InboxEventConsumer {
    return InboxEventConsumer(eventHandlers, processor)
  }
}
