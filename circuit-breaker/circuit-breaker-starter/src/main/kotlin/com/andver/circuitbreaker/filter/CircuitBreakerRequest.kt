package com.andver.circuitbreaker.filter

import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient

object CircuitBreakerRequest {
  const val COMMAND_ATTRIBUTE: String = "circuitBreaker.command"
}

fun ClientRequest.Builder.circuitBreakerCommand(command: String): ClientRequest.Builder =
  attribute(CircuitBreakerRequest.COMMAND_ATTRIBUTE, command)

fun <S : WebClient.RequestHeadersSpec<S>> S.circuitBreakerCommand(command: String): S =
  attribute(CircuitBreakerRequest.COMMAND_ATTRIBUTE, command)
