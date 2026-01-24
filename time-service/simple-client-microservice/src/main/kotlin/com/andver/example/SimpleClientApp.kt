package com.andver.example

import com.andver.time.starter.service.LogicalTimeService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class SimpleClientApp

fun main(args: Array<String>) {
  runApplication<SimpleClientApp>(*args)
}

@Component
class TimeDemo(
  private val logicalTimeService: LogicalTimeService
) : CommandLineRunner {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun run(vararg args: String?) {
    val logicalTime = logicalTimeService.getLogicalTime()
    val logicalInstant = logicalTimeService.getLogicalInstant()

    log.info("Logical time: $logicalTime")
    log.info("Logical instant: $logicalInstant")
    log.info("Local time: ${System.currentTimeMillis()}")

    val offset = logicalTime - System.currentTimeMillis()
    log.info("Clock offset: ${offset}ms")
  }
}

