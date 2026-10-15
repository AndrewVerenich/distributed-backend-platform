package com.andver.gateway.websocket.handler

import com.andver.gateway.websocket.metrics.WebsocketMetrics
import com.andver.gateway.websocket.service.AuthorizationService
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.core.io.buffer.NettyDataBufferFactory
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.HandshakeInfo
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.adapter.NettyWebSocketSessionSupport
import org.springframework.web.reactive.socket.adapter.ReactorNettyWebSocketSession
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServerResponse
import java.util.function.Supplier

private const val AUTHORIZATION_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "
const val USER_ID = "userId"

@Component
class HandshakeHandler(
  private val authorizationService: AuthorizationService,
  private val metrics: WebsocketMetrics,
) : RequestUpgradeStrategy {

  override fun upgrade(
    exchange: ServerWebExchange,
    webSocketHandler: WebSocketHandler,
    subProtocol: String?,
    handshakeInfoFactory: Supplier<HandshakeInfo>
  ): Mono<Void> {
    val response = exchange.response
    val reactorResponse: HttpServerResponse = ServerHttpResponseDecorator.getNativeResponse(response)
    val handshakeInfo = handshakeInfoFactory.get()

    val token = extractToken(exchange)
    val userId = authorizationService.provideUserId(token)

    return response.setComplete()
      .then(
        Mono.defer {
          if (userId != null) {
            metrics.handshakeOk()
            reactorResponse.sendWebsocket { income, out ->
              ReactorNettyWebSocketSession(
                income,
                out,
                handshakeInfo,
                response.bufferFactory() as NettyDataBufferFactory,
                NettyWebSocketSessionSupport.DEFAULT_FRAME_MAX_SIZE
              ).let { session ->
                log.info("Connection established for userId=$userId")
                metrics.connectionOpened()
                session.attributes[USER_ID] = userId
                webSocketHandler.handle(session)
                  .doFinally {
                    metrics.connectionClosed()
                    log.info("Connection closed for userId=$userId")
                  }
              }
            }
          } else {
            metrics.handshakeUnauthorized()
            log.warn("Unauthorized request - invalid or missing token")
            reactorResponse.status(HttpResponseStatus.UNAUTHORIZED).then()
          }
        }
      )
  }

  private fun extractToken(exchange: ServerWebExchange): String? {
    val authHeader = exchange.request.headers.getFirst(AUTHORIZATION_HEADER)
    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) {
      return authHeader.substring(BEARER_PREFIX.length).trim()
    }
    return null
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
