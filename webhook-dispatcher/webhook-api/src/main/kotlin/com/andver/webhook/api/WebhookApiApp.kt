package com.andver.webhook.api

import com.andver.webhook.store.WebhookStoreConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(WebhookStoreConfig::class)
class WebhookApiApp

fun main(args: Array<String>) {
  runApplication<WebhookApiApp>(*args)
}
