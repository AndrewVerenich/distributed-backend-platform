package com.andver.taskrunner.model

import com.andver.taskrunner.entity.TaskStatus

data class TaskExecutionStatusMessage(
  val uuid: String,
  val status: TaskStatus,
)
