package com.andver.gateway.websocket.service.processor.system.handler

import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import reactor.core.publisher.Mono

interface SystemMessageHandler {
  val type: String
  fun handle(message: InternalClientWebSocketEvent): Mono<Void>
}
