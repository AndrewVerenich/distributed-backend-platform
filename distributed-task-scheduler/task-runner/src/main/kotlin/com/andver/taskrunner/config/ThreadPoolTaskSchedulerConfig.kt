package com.andver.taskrunner.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ThreadPoolTaskSchedulerConfig(
  @Value("\${scheduler.pool-size}") private val schedulerPoolSize: Int,
) {

  @Bean
  fun threadPoolTaskScheduler(): ThreadPoolTaskScheduler {
    return ThreadPoolTaskScheduler().apply {
      threadNamePrefix = "cron-scheduler-"
      poolSize = schedulerPoolSize
      initialize()
    }
  }
}