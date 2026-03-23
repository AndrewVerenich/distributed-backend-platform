package com.andver.hash.router.controller

import com.andver.hash.router.routing.RequestRouter
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/route")
class RouterController(
  private val requestRouter: RequestRouter,
) {
  @PostMapping("/{routingKey}")
  fun route(
    @PathVariable routingKey: String,
    @RequestBody(required = false) payload: String?,
  ): Mono<ResponseEntity<String>> {
    return requestRouter.route(routingKey, payload)
  }
}
