package com.andver.task.starter

import com.andver.task.starter.controller.TaskController
import com.andver.task.starter.dispatcher.DefaultTaskDispatcher
import com.andver.task.starter.dispatcher.TaskDispatcher
import com.andver.task.starter.handler.DefaultTaskExecutionHandler
import com.andver.task.starter.handler.TaskExecutionHandler
import com.andver.task.starter.model.Task
import com.andver.task.starter.model.TaskExecutionStatusMessage
import com.andver.task.starter.producer.DefaultTaskStatusProducer
import com.andver.task.starter.producer.TaskStatusProducer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@AutoConfiguration
class TaskAutoConfiguration(
  private val kafkaProperties: KafkaProperties
) {

  @Bean
  fun producerFactory(): ProducerFactory<String, TaskExecutionStatusMessage> {
    return DefaultKafkaProducerFactory(kafkaProperties.buildProducerProperties())
  }

  @Bean
  fun kafkaTemplate(): KafkaTemplate<String, TaskExecutionStatusMessage> {
    return KafkaTemplate(producerFactory())
  }

  @Bean
  fun taskStatusProducer(
    kafkaTemplate: KafkaTemplate<String, TaskExecutionStatusMessage>,
    @Value("\${task.status-topic:task-execution-status}") statusTopic: String,
  ): TaskStatusProducer {
    return DefaultTaskStatusProducer(kafkaTemplate, statusTopic)
  }

  @Bean
  fun taskExecutionHandlers(
    tasks: List<Task>? = emptyList(),
    taskStatusProducer: TaskStatusProducer,
  ): List<TaskExecutionHandler> {
    return tasks?.map { task -> DefaultTaskExecutionHandler(task, taskStatusProducer) } ?: emptyList()
  }

  @Bean
  fun taskDispatcher(
    taskExecutionHandlers: List<TaskExecutionHandler>,
    taskStatusProducer: TaskStatusProducer,
  ): TaskDispatcher {
    return DefaultTaskDispatcher(taskExecutionHandlers, taskStatusProducer)
  }

  @Bean
  fun taskController(taskDispatcher: TaskDispatcher): TaskController {
    return TaskController(taskDispatcher)
  }
}