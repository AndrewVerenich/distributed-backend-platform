package com.andver.taskrunner.consumer

import com.andver.taskrunner.handler.TaskExecutionHandler
import com.andver.taskrunner.model.TaskExecutionStatusMessage
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TaskExecutionStatusConsumer(
  private val taskExecutionHandler: TaskExecutionHandler
) {
  private val log = LoggerFactory.getLogger(TaskExecutionStatusConsumer::class.java)

  @KafkaListener(topics = ["\${scheduler.status-topic}"])
  fun consume(message: TaskExecutionStatusMessage) {
    taskExecutionHandler.update(message.uuid, message.status)
      .doOnError { e -> log.error("Error while consuming record=$message", e) }
      .doOnSuccess { log.info("Received task execution status =$message") }
      .subscribe()
  }
}