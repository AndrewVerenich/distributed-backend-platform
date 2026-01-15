package com.andver.clientdeduplicator.starter.inserter

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

const val X_CAPTURED_BODY_HEADER = "X-Request-Body-Captured"

class CapturingBodyInserter<T>(
  private val value: T,
  private val onCaptured: (String) -> Unit
) : BodyInserter<T, ClientHttpRequest> {

  private val mapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

  override fun insert(
    outputMessage: ClientHttpRequest,
    context: BodyInserter.Context
  ): Mono<Void> {
    val json = mapper.writeValueAsString(value)
    onCaptured(json)
    outputMessage.headers.add(X_CAPTURED_BODY_HEADER, json)
    val buffer = outputMessage.bufferFactory().wrap(json.toByteArray())
    return outputMessage.writeWith(Mono.just(buffer))
  }
}

fun <T> WebClient.RequestBodySpec.capturedBody(
  body: T,
  onCaptured: (String) -> Unit = {}
): WebClient.RequestHeadersSpec<*> {
  return this.body(CapturingBodyInserter(body, onCaptured))
}
