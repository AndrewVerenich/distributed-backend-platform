package com.andver.gateway.websocket.service.processor.system.handler

import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class PingMessageHandler : SystemMessageHandler {
  override val type = "PING"

  override fun handle(message: InternalClientWebSocketEvent): Mono<Void> {
    log.debug("---> Ping message from userId={}", message.userId)
    return Mono.empty()
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
