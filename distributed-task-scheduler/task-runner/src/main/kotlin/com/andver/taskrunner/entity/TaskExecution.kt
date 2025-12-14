package com.andver.taskrunner.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("task_execution")
data class TaskExecution(
  @Id
  val id: Long? = null,
  val uuid: String,
  val startTime: LocalDateTime,
  val finishTime: LocalDateTime? = null,
  val name: String,
  val component: String,
  val status: TaskStatus,
)

enum class TaskStatus {
  PREPARE,
  IN_PROGRESS,
  FINISHED,
  ERROR,
  SKIPPED,
}
