package com.andver.id.generator.controller

import com.andver.id.generator.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class IdGeneratorController(
  private val idGenerator: IdGenerator,
) {
  private val logger = LoggerFactory.getLogger(IdGeneratorController::class.java)

  @GetMapping("/id")
  fun generateId(): Mono<Long> {
    return Mono.fromCallable { idGenerator.generateId() }
      .doOnNext { logger.info("Generated id: $it") }
  }
}
