package com.andver.task.starter.model

data class RunTaskParams(
  val uuid: String,
  val name: String,
  val params: Map<String, Any>
)
