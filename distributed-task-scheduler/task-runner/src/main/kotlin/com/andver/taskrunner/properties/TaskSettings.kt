package com.andver.taskrunner.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "scheduler")
data class TaskSettings(
  val tasks: Map<String, Task>,
)

data class Task(
  val cron: String,
  val component: String,
  val params: Map<String, Any>,
)
