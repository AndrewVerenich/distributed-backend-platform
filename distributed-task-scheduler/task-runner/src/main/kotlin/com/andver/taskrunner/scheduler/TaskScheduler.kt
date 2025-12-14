package com.andver.taskrunner.scheduler

import com.andver.taskrunner.properties.TaskSettings
import com.andver.taskrunner.runner.TaskRunner
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component

interface TaskScheduler {
  fun scheduleTasks()
}

@Component
class CronTaskScheduler(
  private val taskSettings: TaskSettings,
  private val threadPoolTaskScheduler: ThreadPoolTaskScheduler,
  private val taskRunner: TaskRunner,
) : TaskScheduler {

  private val logger = LoggerFactory.getLogger(TaskScheduler::class.java)

  @EventListener(ContextRefreshedEvent::class)
  override fun scheduleTasks() {
    taskSettings.tasks.forEach { taskName, task ->
      runCatching { CronTrigger(task.cron) }
        .onFailure { exception ->
          throw IllegalStateException(
            "[${taskName}] Invalid cron expression = $task.cron", exception
          )
        }
        .map { cronExpression ->
          logger.info("[${taskName}] Schedule | cron = $cronExpression")
          threadPoolTaskScheduler.schedule({
            taskRunner.run(taskName, task)
              .doOnError { exception -> logger.error("[${taskName}] Fail to run task", exception) }
              .subscribe()
          }, cronExpression)
        }
    }
  }
}