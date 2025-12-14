package com.andver.taskrunner.runner

import com.andver.taskrunner.connector.ComponentConnector
import com.andver.taskrunner.entity.TaskStatus
import com.andver.taskrunner.handler.TaskExecutionHandler
import com.andver.taskrunner.model.RunTaskParams
import com.andver.taskrunner.properties.Task
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*

interface TaskRunner {
  fun run(taskName: String, task: Task): Mono<Void>
}

@Component
class DefaultTaskRunner(
  private val connector: ComponentConnector,
  private val handler: TaskExecutionHandler,
) : TaskRunner {
  private val logger = LoggerFactory.getLogger(DefaultTaskRunner::class.java)
  override fun run(taskName: String, task: Task): Mono<Void> {
    val uuid = UUID.randomUUID().toString()
    logger.info("[${taskName}] Running task uuid = $uuid")
    val params = RunTaskParams(uuid, taskName, task.params)
    return handler.prepare(taskName, uuid, task.component)
      .then(
        connector.runTask(task.component, params)
          .onErrorResume {
            logger.error("[${taskName}] Task running failed uuid = $uuid")
            handler.update(uuid, TaskStatus.ERROR)
          }
      )
  }
}