package com.andver.example.order

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
class SimpleOrderMicroserviceApp

fun main(args: Array<String>) {
  SpringApplication.run(SimpleOrderMicroserviceApp::class.java, *args)
}

@Component
class CheckOrderDeliveryTask : Task {
  override val taskName: String = "checkOrderDelivery"
  override val scheduler: Scheduler = Schedulers.boundedElastic()
  private val logger = LoggerFactory.getLogger(this.javaClass)

  override fun execute(param: Map<String, Any>): Mono<Void> {
    logger.info("Checking order delivery with batchsize [${param["batchsize"]}]")
    // business logic
    return Mono.delay(Duration.ofSeconds(60))
      .then()
  }
}