package com.andver.webhook.processor

import com.andver.webhook.store.WebhookStoreConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@Import(WebhookStoreConfig::class)
class EventProcessorApp

fun main(args: Array<String>) {
  runApplication<EventProcessorApp>(*args)
}
