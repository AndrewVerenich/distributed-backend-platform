package com.andver.gateway.websocket.service.processor

import com.andver.gateway.websocket.model.ClientEventTarget
import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import reactor.core.publisher.Mono

interface ClientMessageProcessor {
  val target: ClientEventTarget
  fun process(message: InternalClientWebSocketEvent): Mono<Void>
}
