package com.andver.idempotency.server.owner

import org.springframework.http.server.reactive.ServerHttpRequest

fun interface OwnerResolver {
  fun resolve(request: ServerHttpRequest): String?
}
