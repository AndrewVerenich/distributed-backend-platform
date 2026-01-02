package com.andver.gateway.websocket.handler

import com.andver.gateway.websocket.model.InternalClientWebSocketEvent
import com.andver.gateway.websocket.service.UserSubscriptionService
import com.andver.gateway.websocket.service.dispatcher.ClientMessageDispatcher
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

@Component
class UserChannelHandler(
  private val userSubscriptionService: UserSubscriptionService,
  private val clientMessageDispatcher: ClientMessageDispatcher,
  private val objectMapper: ObjectMapper,
) : WebSocketHandler {

  override fun handle(session: WebSocketSession): Mono<Void> {
    val userId = session.attributes[USER_ID] as Long
    val incomingMessages = session.receive()
      .doOnNext { log.debug("---> Received message from userId={}, message={}", userId, it.payloadAsText) }
      .mapNotNull { message -> deserialize(message, userId) }
      .flatMap { event -> clientMessageDispatcher.dispatch(event!!) }

    val outcomeMessages = session.send(
      userSubscriptionService.subscribeToUserNotifications(userId)
        .doOnNext { log.debug("<--- Send message to userId={}, message={}", userId, it) }
        .map(session::textMessage)
    )
    return outcomeMessages
      .and(incomingMessages)
      .then()
  }

  private fun deserialize(message: WebSocketMessage, userId: Long): InternalClientWebSocketEvent? {
    return try {
      objectMapper.readValue(message.payloadAsText, InternalClientWebSocketEvent::class.java).apply {
        this.userId = userId
      }
    } catch (ex: Exception) {
      log.error("---> Cannot deserialize message for userId=$userId, message =${message.payloadAsText}", ex)
      null
    }
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
