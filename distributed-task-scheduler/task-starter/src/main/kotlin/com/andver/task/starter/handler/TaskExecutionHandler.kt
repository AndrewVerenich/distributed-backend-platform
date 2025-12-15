package com.andver.task.starter.handler

import com.andver.task.starter.model.RunTaskParams
import com.andver.task.starter.model.Task
import com.andver.task.starter.model.TaskStatus.ERROR
import com.andver.task.starter.model.TaskStatus.FINISHED
import com.andver.task.starter.model.TaskStatus.IN_PROGRESS
import com.andver.task.starter.model.TaskStatus.SKIPPED
import com.andver.task.starter.producer.TaskStatusProducer
import org.redisson.api.RedissonReactiveClient
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

interface TaskExecutionHandler {
  fun executeTaskAsync(runTaskParams: RunTaskParams): Mono<Void>
  val task: Task
}

class DefaultTaskExecutionHandler(
  override val task: Task,
  private val taskStatusProducer: TaskStatusProducer,
  private val redissonClient: RedissonReactiveClient
) : TaskExecutionHandler {
  private val logger = LoggerFactory.getLogger(DefaultTaskExecutionHandler::class.java)

  override fun executeTaskAsync(runTaskParams: RunTaskParams): Mono<Void> {
    val lock = redissonClient.getLock("task:${runTaskParams.name}")
    Mono.usingWhen(
      lock.tryLock(),
      { isLockAcquired ->
        if (isLockAcquired) {
          task.execute(runTaskParams.params)
            .doOnSubscribe {
              logger.info("Starting ${task.taskName} task")
              taskStatusProducer.sendStatus(IN_PROGRESS, runTaskParams.uuid)
            }
            .doOnSuccess {
              logger.info("Task ${task.taskName} finished")
              taskStatusProducer.sendStatus(FINISHED, runTaskParams.uuid)
            }
            .doOnError {
              logger.error("Task ${task.taskName} failed", it)
              taskStatusProducer.sendStatus(ERROR, runTaskParams.uuid)
            }
        } else {
          Mono.fromCallable {
            taskStatusProducer.sendStatus(SKIPPED, runTaskParams.uuid)
            logger.warn("Task {} is already executed", task.taskName)
          }
        }
      },
      { isLockAcquired -> if (isLockAcquired) lock.forceUnlock() else Mono.empty<Boolean>() }
    )
      .doOnError {
        logger.error("Task ${task.taskName} failed", it)
        taskStatusProducer.sendStatus(ERROR, runTaskParams.uuid)
      }
      .subscribeOn(task.scheduler)
      .subscribe()
    return Mono.empty()
  }
}