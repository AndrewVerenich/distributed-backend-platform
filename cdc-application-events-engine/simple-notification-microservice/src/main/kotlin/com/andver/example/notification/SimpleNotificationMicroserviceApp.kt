package com.andver.example.notification

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@SpringBootApplication
class SimpleNotificationMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleNotificationMicroserviceApp::class.java, *args)
}

@Component
class KafkaListener {
  private val logger = LoggerFactory.getLogger(this.javaClass)

  @KafkaListener(topics = ["#{'\${topics}'.split(',')}"], groupId = "notification")
  fun consume(consumerRecord: ConsumerRecord<String, String>) {
    logger.info("Consume message: topic=${consumerRecord.topic()}, key=${consumerRecord.key()}, value=${consumerRecord.value()}")
  }
}