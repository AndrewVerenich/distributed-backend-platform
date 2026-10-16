package com.andver.idempotency.server.owner

import com.andver.idempotency.server.IdempotencyServerProperties
import org.springframework.http.server.reactive.ServerHttpRequest

class HeaderOwnerResolver(
  private val properties: IdempotencyServerProperties,
) : OwnerResolver {
  override fun resolve(request: ServerHttpRequest): String? =
    request.headers.getFirst(properties.ownerHeader)?.takeIf { it.isNotBlank() }
}
