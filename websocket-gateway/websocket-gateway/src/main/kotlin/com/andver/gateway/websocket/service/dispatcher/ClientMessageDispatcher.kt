package com.andver.gateway.websocket.service.dispatcher

import com.andver.gateway.websocket.model.ClientEventTarget
import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import com.andver.gateway.websocket.service.processor.ClientMessageProcessor
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

interface ClientMessageDispatcher {
  fun dispatch(message: InternalClientWebSocketEvent): Mono<Void>
}

@Component
class DefaultClientMessageDispatcher(
  clientMessageProcessors: List<ClientMessageProcessor>
) : ClientMessageDispatcher {
  private val targetToProcessor: Map<ClientEventTarget, ClientMessageProcessor> =
    clientMessageProcessors.associateBy { it.target }

  override fun dispatch(message: InternalClientWebSocketEvent): Mono<Void> {
    val target = ClientEventTarget.fromString(message.target)
    return target?.let {
      targetToProcessor[it]?.process(message)
    } ?: Mono.just(message)
      .doOnNext { log.warn("---> Unknown event target for message=$message") }
      .then()
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
