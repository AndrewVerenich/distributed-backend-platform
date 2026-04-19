package com.andver.saga.orchestrator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SagaOrchestratorApp

fun main(args: Array<String>) {
  runApplication<SagaOrchestratorApp>(*args)
}
