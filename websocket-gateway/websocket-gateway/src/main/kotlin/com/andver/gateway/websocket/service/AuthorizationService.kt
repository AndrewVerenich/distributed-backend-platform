package com.andver.gateway.websocket.service

import org.springframework.stereotype.Component

interface AuthorizationService {
  fun provideUserId(channelId: String?): Long?
}

@Component
class DefaultAuthorizationService(
) : AuthorizationService {
  override fun provideUserId(channelId: String?): Long? {
    return 1
  }
}
