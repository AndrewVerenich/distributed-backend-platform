package com.andver.task.starter.producer

import com.andver.task.starter.model.TaskExecutionStatusMessage
import com.andver.task.starter.model.TaskStatus
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

interface TaskStatusProducer {
  fun sendStatus(status: TaskStatus, uuid: String)
}

class DefaultTaskStatusProducer(
  private val kafkaTemplate: KafkaTemplate<String, TaskExecutionStatusMessage>,
  private val statusTopic: String,
) : TaskStatusProducer {
  private val logger = LoggerFactory.getLogger(DefaultTaskStatusProducer::class.java)

  override fun sendStatus(status: TaskStatus, uuid: String) {
    val message = TaskExecutionStatusMessage(uuid, status)
    logger.info("Sending task status: $message")
    kafkaTemplate.send(ProducerRecord(statusTopic, uuid, message))
  }
}
