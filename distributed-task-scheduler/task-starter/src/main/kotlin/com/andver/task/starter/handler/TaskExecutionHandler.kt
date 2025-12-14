package com.andver.task.starter.handler

import com.andver.task.starter.model.RunTaskParams
import com.andver.task.starter.model.Task
import com.andver.task.starter.model.TaskStatus.ERROR
import com.andver.task.starter.model.TaskStatus.FINISHED
import com.andver.task.starter.model.TaskStatus.IN_PROGRESS
import com.andver.task.starter.model.TaskStatus.SKIPPED
import com.andver.task.starter.producer.TaskStatusProducer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

interface TaskExecutionHandler {
  fun executeTaskAsync(runTaskParams: RunTaskParams): Mono<Void>
  val task: Task
}

class DefaultTaskExecutionHandler(
  override val task: Task,
  private val taskStatusProducer: TaskStatusProducer,
) : TaskExecutionHandler {
  private val executed = AtomicBoolean(false)
  private val logger = LoggerFactory.getLogger(DefaultTaskExecutionHandler::class.java)

  override fun executeTaskAsync(runTaskParams: RunTaskParams): Mono<Void> {
    if (executed.compareAndSet(false, true)) {
      task.execute(param = runTaskParams.params)
        .doOnSubscribe {
          logger.info("Starting ${task.taskName} task")
          taskStatusProducer.sendStatus(status = IN_PROGRESS, uuid = runTaskParams.uuid)
        }
        .doOnSuccess {
          logger.info("Task ${task.taskName} finished")
          taskStatusProducer.sendStatus(status = FINISHED, uuid = runTaskParams.uuid)
        }
        .doOnError {
          logger.error("Task ${task.taskName} failed", it)
          taskStatusProducer.sendStatus(status = ERROR, uuid = runTaskParams.uuid)
        }
        .doFinally { executed.set(false) }
        .subscribeOn(task.scheduler)
        .subscribe()
    } else {
      taskStatusProducer.sendStatus(status = SKIPPED, uuid = runTaskParams.uuid)
      logger.warn("Task {} is already executed", task.taskName)
    }
    return Mono.empty()
  }
}