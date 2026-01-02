package com.andver.gateway.websocket.model

enum class ClientEventTarget {
  DOMAIN,
  SYSTEM;

  companion object {
    fun fromString(name: String): ClientEventTarget? {
      return ClientEventTarget.entries.firstOrNull { it.name == name }
    }
  }
}
