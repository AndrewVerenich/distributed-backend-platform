package com.andver.taskrunner.model

data class RunTaskParams(
  val uuid: String,
  val name: String,
  val params: Map<String, Any>
)
