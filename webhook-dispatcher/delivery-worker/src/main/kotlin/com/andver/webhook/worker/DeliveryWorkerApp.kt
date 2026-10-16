package com.andver.webhook.worker

import com.andver.webhook.store.WebhookStoreConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableKafka
@EnableScheduling
@Import(WebhookStoreConfig::class)
class DeliveryWorkerApp

fun main(args: Array<String>) {
  runApplication<DeliveryWorkerApp>(*args)
}
