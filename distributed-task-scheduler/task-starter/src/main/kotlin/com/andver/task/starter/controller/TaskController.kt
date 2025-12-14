package com.andver.task.starter.controller

import com.andver.task.starter.dispatcher.TaskDispatcher
import com.andver.task.starter.model.RunTaskParams
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class TaskController(
  private val taskDispatcher: TaskDispatcher,
) {
  private val logger = LoggerFactory.getLogger(TaskController::class.java)

  @PostMapping("/scheduled-task/run")
  fun runTask(@RequestBody param: RunTaskParams): Mono<Void> {
    logger.debug("Receiver run task request with param = {} ", param)
    return taskDispatcher.dispatch(param)
  }
}