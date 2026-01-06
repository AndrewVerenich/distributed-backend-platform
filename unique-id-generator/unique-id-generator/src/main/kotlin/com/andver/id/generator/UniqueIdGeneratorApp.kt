package com.andver.id.generator

import com.netflix.discovery.EurekaClient
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class UniqueIdGeneratorApp

fun main(args: Array<String>) {
  SpringApplication.run(UniqueIdGeneratorApp::class.java, *args)
}

@RestController
class GeneratorController(
  private val eurekaClient: EurekaClient
) {

  @GetMapping("/test")
  fun test(): String {
    return eurekaClient.applicationInfoManager.info.instanceId
  }
}