package com.andver.gateway.websocket.handler

import com.andver.gateway.websocket.service.AuthorizationService
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
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
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

private const val CHANNEL_ID_PARAM = "id"
const val USER_ID = "userId"

@Component
class HandshakeHandler(
  private val authorizationService: AuthorizationService,
  meterRegistry: MeterRegistry,
) : RequestUpgradeStrategy {
  private final val connectionsCount = AtomicLong()

  init {
    Gauge.builder("websocket.connections.total", connectionsCount::get).register(meterRegistry)
  }

  override fun upgrade(
    exchange: ServerWebExchange,
    webSocketHandler: WebSocketHandler,
    subProtocol: String?,
    handshakeInfoFactory: Supplier<HandshakeInfo>
  ): Mono<Void> {
    val response = exchange.response
    val reactorResponse: HttpServerResponse = ServerHttpResponseDecorator.getNativeResponse(response)
    val handshakeInfo = handshakeInfoFactory.get()

    val channelId = handshakeInfo.uri.query.removePrefix("$CHANNEL_ID_PARAM=")
    val userId = authorizationService.provideUserId(channelId)

    return response.setComplete()
      .then(
        Mono.defer {
          if (userId != null) {
            reactorResponse.sendWebsocket { income, out ->
              ReactorNettyWebSocketSession(
                income,
                out,
                handshakeInfo,
                response.bufferFactory() as NettyDataBufferFactory,
                NettyWebSocketSessionSupport.DEFAULT_FRAME_MAX_SIZE
              ).let { session ->
                log.info("Connection established for userId=$userId, channelId=$channelId")
                connectionsCount.incrementAndGet()
                session.attributes[USER_ID] = userId
                webSocketHandler.handle(session)
                  .doFinally {
                    connectionsCount.decrementAndGet()
                    log.info("Connection closed for userId=$userId, channelId=$channelId")
                  }
              }
            }
          } else {
            log.warn("Unauthorized request for channelId=$channelId")
            reactorResponse.status(HttpResponseStatus.UNAUTHORIZED).then()
          }
        }
      )
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}
