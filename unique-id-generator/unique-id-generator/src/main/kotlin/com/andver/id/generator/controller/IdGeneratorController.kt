package com.andver.id.generator.controller

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IdGeneratorController {
  private val logger = LoggerFactory.getLogger(IdGeneratorController::class.java)
  @GetMapping("/id")
  fun generateId(): Long {
    logger.info("Generating ID")
    return 100L
  }
}
