package com.andver.example.id.generator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executors

@SpringBootApplication
class SimpleNotificationMicroserviceApp(
  @Value("\${client.pool-size}") private val nThreads: Int,
) : CommandLineRunner {

  private val logger = LoggerFactory.getLogger(SimpleNotificationMicroserviceApp::class.java)

  override fun run(vararg args: String?) {
    val dispatcher = Executors.newFixedThreadPool(nThreads).asCoroutineDispatcher()
    val restTemplate = RestTemplate()

    val scope = CoroutineScope(dispatcher)

    repeat(nThreads) { worker ->
      scope.launch {
        while (true) {
          try {
            val response = restTemplate.getForObject("http://load-balancer:8888/id", Long::class.java)
            logger.info("Worker $worker received id: $response")
          } catch (ex: Exception) {
            logger.error("Worker $worker error:", ex)
          }
        }
      }
    }
  }
}

fun main(args: Array<String>) {
  SpringApplication.run(SimpleNotificationMicroserviceApp::class.java, *args)
}



