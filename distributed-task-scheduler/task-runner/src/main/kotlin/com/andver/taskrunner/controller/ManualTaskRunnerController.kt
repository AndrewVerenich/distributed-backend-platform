package com.andver.taskrunner.controller

import com.andver.taskrunner.properties.TaskSettings
import com.andver.taskrunner.runner.TaskRunner
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class ManualTaskRunnerController(
  private val taskRunner: TaskRunner,
  private val taskSettings: TaskSettings,
) {
  @PostMapping("/run")
  fun manualRun(@RequestBody params: ManualRunParams): Mono<Void> {
    val taskName = params.taskName
    val task = taskSettings.tasks[taskName] ?: throw IllegalArgumentException("Not found task '$taskName'")
    return taskRunner.run(taskName, task).then()
  }
}

data class ManualRunParams(
  val taskName: String,
)