package com.andver.gateway.push.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GatewayExceptionHandler {

  @ExceptionHandler(ResponseStatusException::class)
  fun handle(ex: ResponseStatusException): ResponseEntity<Map<String, Any?>> {
    val headers = HttpHeaders()
    if (ex.statusCode == HttpStatus.SERVICE_UNAVAILABLE) {
      headers.set("Retry-After", "1")
    }
    return ResponseEntity.status(ex.statusCode)
      .headers(headers)
      .body(
        mapOf(
          "status" to ex.statusCode.value(),
          "message" to (ex.reason ?: "error"),
        ),
      )
  }
}
