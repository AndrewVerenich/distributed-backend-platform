package com.andver.taskrunner.repository

import com.andver.taskrunner.entity.TaskExecution
import com.andver.taskrunner.entity.TaskStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.time.LocalDateTime

interface TaskExecutionRepository : ReactiveCrudRepository<TaskExecution, Long> {
  @Query("UPDATE task_execution SET finish_time = :finishTime, status = :status WHERE uuid = :uuid")
  fun updateFinishTimeAndStatusByUuid(
    finishTime: LocalDateTime?,
    status: TaskStatus,
    uuid: String
  ): Mono<Void>
}