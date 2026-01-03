package com.andver.gateway.websocket.service

import org.springframework.stereotype.Component

interface AuthorizationService {
  fun provideUserId(token: String?): Long?
}

@Component
class DefaultAuthorizationService(
  private val jwtService: JwtService,
) : AuthorizationService {
  override fun provideUserId(token: String?): Long? {
    if (token == null) {
      return null
    }
    return jwtService.validateToken(token).userId
  }
}
