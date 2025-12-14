package com.andver.example.user

import com.andver.task.starter.model.Task
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Duration

@SpringBootApplication
class SimpleUserMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleUserMicroserviceApp::class.java, *args)
}

@Component
class BlockInactiveUsersTask : Task {
  override val taskName: String = "blockInactiveUsers"
  override val scheduler: Scheduler = Schedulers.boundedElastic()
  private val logger = LoggerFactory.getLogger(this.javaClass)

  override fun execute(param: Map<String, Any>): Mono<Void> {
    logger.info("Block inactive users with params = $param")
    // business logic
    return Mono.delay(Duration.ofSeconds(3))
      .then()
  }
}