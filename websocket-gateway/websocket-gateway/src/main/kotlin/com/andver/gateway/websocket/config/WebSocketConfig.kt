package com.andver.gateway.websocket.config

import com.andver.gateway.websocket.handler.HandshakeHandler
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.WebSocketService
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.netty.http.server.HttpServer

@Configuration
@EnableWebFlux
class WebSocketConfig(
  private val webSocketHandler: WebSocketHandler,
  private val handshakeHandler: HandshakeHandler,
) {
  @Bean
  fun urlHandlerMapping(): SimpleUrlHandlerMapping {
    return SimpleUrlHandlerMapping(mapOf("/channels" to webSocketHandler))
  }

  @Bean
  fun handlerAdapter() = WebSocketHandlerAdapter(webSocketService())

  @Bean
  fun webSocketService(): WebSocketService {
    return HandshakeWebSocketService(handshakeHandler)
  }

  @Bean
  fun nettyServerCustomizer(): NettyServerCustomizer {
    // enable netty metrics
    return NettyServerCustomizer { httpServer: HttpServer -> httpServer.metrics(true) { it -> it } }
  }
}
