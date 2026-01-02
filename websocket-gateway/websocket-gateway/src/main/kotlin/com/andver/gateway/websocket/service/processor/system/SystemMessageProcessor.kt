package com.andver.gateway.websocket.service.processor.system

import com.andver.gateway.websocket.model.ClientEventTarget
import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import com.andver.gateway.websocket.service.processor.ClientMessageProcessor
import com.andver.gateway.websocket.service.processor.system.handler.SystemMessageHandler
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SystemMessageProcessor(
  systemMessageHandlers: List<SystemMessageHandler>
) : ClientMessageProcessor {
  override val target = ClientEventTarget.SYSTEM

  private val typeToHandler: Map<String, SystemMessageHandler> = systemMessageHandlers.associateBy { it.type }

  override fun process(message: InternalClientWebSocketEvent): Mono<Void> {
    return typeToHandler[message.type]?.handle(message) ?: Mono.just(message)
      .doOnNext { log.warn("---> Unknown event type for SYSTEM target message=$message") }
      .then()
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
