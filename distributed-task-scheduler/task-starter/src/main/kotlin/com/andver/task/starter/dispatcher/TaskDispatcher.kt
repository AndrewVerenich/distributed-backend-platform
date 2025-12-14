package com.andver.task.starter.dispatcher

import com.andver.task.starter.handler.TaskExecutionHandler
import com.andver.task.starter.model.RunTaskParams
import com.andver.task.starter.model.TaskStatus
import com.andver.task.starter.producer.TaskStatusProducer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

interface TaskDispatcher {
  fun dispatch(param: RunTaskParams): Mono<Void>
}

class DefaultTaskDispatcher(
  handlers: List<TaskExecutionHandler>,
  private val taskStatusProducer: TaskStatusProducer,
) : TaskDispatcher {
  private var taskNameToTaskHandler: Map<String, TaskExecutionHandler> = handlers.associateBy { it.task.taskName }
  private val logger = LoggerFactory.getLogger(DefaultTaskDispatcher::class.java)

  override fun dispatch(param: RunTaskParams): Mono<Void> {
    return Mono.just(param)
      .handle { taskRunParam, sync ->
        val taskHandler = taskNameToTaskHandler[taskRunParam.name]
        if (taskHandler == null) {
          taskStatusProducer.sendStatus(status = TaskStatus.ERROR, uuid = taskRunParam.uuid)
          logger.warn("Cannot find configured task with name: ${taskRunParam.name}")
        } else {
          sync.next(taskHandler)
        }
      }
      .flatMap { taskHandler ->
        taskHandler.executeTaskAsync(param)
      }
  }
}