package com.andver.taskrunner.handler

import com.andver.taskrunner.entity.TaskExecution
import com.andver.taskrunner.entity.TaskStatus
import com.andver.taskrunner.repository.TaskExecutionRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.LocalDateTime

interface TaskExecutionHandler {
  fun prepare(name: String, uuid: String, component: String): Mono<Void>
  fun update(uuid: String, status: TaskStatus): Mono<Void>
}

@Component
class DefaultTaskExecutionHandler(
  private val taskExecutionRepository: TaskExecutionRepository,
) : TaskExecutionHandler {
  override fun prepare(
    name: String,
    uuid: String,
    component: String,
  ): Mono<Void> {
    return TaskExecution(
      uuid = uuid,
      startTime = LocalDateTime.now(),
      name = name,
      component = component,
      status = TaskStatus.PREPARE,
    ).let { taskExecution ->
      taskExecutionRepository.save(taskExecution)
        .then()
    }
  }

  override fun update(
    uuid: String,
    status: TaskStatus,
  ): Mono<Void> {
    val finishTime = if (status == TaskStatus.FINISHED) LocalDateTime.now() else null
    return taskExecutionRepository.updateFinishTimeAndStatusByUuid(finishTime, status, uuid)
  }
}