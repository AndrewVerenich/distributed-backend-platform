package com.andver.hash.backend.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BackendController(
  @Value("\${backend.id}") private val backendId: String,
) {
  @GetMapping("/health")
  fun health(): Map<String, String> {
    return mapOf("status" to "UP", "backendId" to backendId)
  }

  @PostMapping("/internal/route/{routingKey}")
  fun routed(
    @PathVariable routingKey: String,
    @RequestBody(required = false) payload: String?,
  ): Map<String, String> {
    return mapOf(
      "backendId" to backendId,
      "routingKey" to routingKey,
      "payload" to (payload ?: ""),
    )
  }
}
