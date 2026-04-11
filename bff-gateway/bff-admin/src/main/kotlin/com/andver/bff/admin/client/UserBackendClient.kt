package com.andver.bff.admin.client

import com.andver.bff.admin.backend.CreateUserBackendRequest
import com.andver.bff.admin.backend.UpdateUserBackendRequest
import com.andver.bff.admin.backend.UserBackendDto
import com.andver.bff.admin.backend.UserStatsBackendDto
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class UserBackendClient(
  private val userWebClient: WebClient,
) {

  fun list(correlationId: String): Flux<UserBackendDto> =
    userWebClient.get()
      .uri("/api/users")
      .header(CORRELATION_HEADER, correlationId)
      .retrieve()
      .bodyToFlux<UserBackendDto>()

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

  fun create(req: CreateUserBackendRequest, correlationId: String): Mono<UserBackendDto> =
    userWebClient.post()
      .uri("/api/users")
      .header(CORRELATION_HEADER, correlationId)
      .bodyValue(req)
      .retrieve()
      .bodyToMono<UserBackendDto>()

  fun update(id: Long, req: UpdateUserBackendRequest, correlationId: String): Mono<UserBackendDto> =
    userWebClient.put()
      .uri("/api/users/{id}", id)
      .header(CORRELATION_HEADER, correlationId)
      .bodyValue(req)
      .retrieve()
      .bodyToMono<UserBackendDto>()

  companion object {
    private const val CORRELATION_HEADER = "X-Correlation-Id"
  }
}
