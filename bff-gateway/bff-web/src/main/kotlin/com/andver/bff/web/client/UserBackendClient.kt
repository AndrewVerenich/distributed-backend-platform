package com.andver.bff.web.client

import com.andver.bff.web.backend.UserBackendDto
import com.andver.bff.web.backend.UserStatsBackendDto
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class UserBackendClient(
  private val userWebClient: WebClient,
) {

  fun getUser(id: Long, correlationId: String): Mono<UserBackendDto> =
    userWebClient.get()
      .uri("/api/users/{id}", id)
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<UserBackendDto>()

  fun stats(correlationId: String): Mono<UserStatsBackendDto> =
    userWebClient.get()
      .uri("/api/users/stats")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToMono<UserStatsBackendDto>()

  companion object {
    private const val CORRELATION_HEADER = "X-Correlation-Id"
  }
}
