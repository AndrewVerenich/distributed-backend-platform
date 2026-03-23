package com.andver.hash.router.routing

import com.andver.hash.router.hash.ConsistentHashRing
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

private const val X_ROUTED_TO = "X-Routed-To"

@Service
class RequestRouter(
  private val consistentHashRing: ConsistentHashRing,
  private val webClientBuilder: WebClient.Builder,
) {
  fun route(routingKey: String, payload: String?): Mono<ResponseEntity<String>> {
    val targetNode = consistentHashRing.resolveNode(routingKey)
      ?: return Mono.just(
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("No healthy backend nodes available")
      )

    return webClientBuilder.build()
      .post()
      .uri("${targetNode.baseUrl()}/internal/route/$routingKey")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(payload ?: "")
      .retrieve()
      .toEntity(String::class.java)
      .map { backendResponse ->
        ResponseEntity.status(backendResponse.statusCode)
          .header(X_ROUTED_TO, targetNode.id)
          .body(backendResponse.body ?: "")
      }
      .onErrorResume {
        Mono.just(
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .header(X_ROUTED_TO, targetNode.id)
            .body("Failed to route request to backend node")
        )
      }
  }
}
