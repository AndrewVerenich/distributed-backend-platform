package com.andver.task.starter.model

import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

interface Task {
  val taskName: String
  val scheduler: Scheduler
  fun execute(param: Map<String, Any>): Mono<Void>
}