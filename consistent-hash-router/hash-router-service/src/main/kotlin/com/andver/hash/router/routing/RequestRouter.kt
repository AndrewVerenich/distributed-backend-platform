package com.andver.hash.router.routing

import com.andver.hash.router.hash.ConsistentHashRing
import com.andver.hash.router.metrics.RouterMetrics
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

private const val X_ROUTED_TO = "X-Routed-To"

@Service
class RequestRouter(
  private val consistentHashRing: ConsistentHashRing,
  private val webClientBuilder: WebClient.Builder,
  private val metrics: RouterMetrics,
) {
  fun route(routingKey: String, payload: String?): Mono<ResponseEntity<String>> {
    val started = System.nanoTime()
    val targetNode = consistentHashRing.resolveNode(routingKey)
      ?: run {
        metrics.recordNoNodes()
        return Mono.just(
          ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("No healthy backend nodes available")
        )
      }

    return webClientBuilder.build()
      .post()
      .uri("${targetNode.baseUrl()}/internal/route/$routingKey")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(payload ?: "")
      .retrieve()
      .toEntity(String::class.java)
      .map { backendResponse ->
        metrics.recordRoute(targetNode.id, "ok", Duration.ofNanos(System.nanoTime() - started))
        ResponseEntity.status(backendResponse.statusCode)
          .header(X_ROUTED_TO, targetNode.id)
          .body(backendResponse.body ?: "")
      }
      .onErrorResume {
        metrics.recordRoute(targetNode.id, "bad_gateway", Duration.ofNanos(System.nanoTime() - started))
        Mono.just(
          ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .header(X_ROUTED_TO, targetNode.id)
            .body("Failed to route request to backend node")
        )
      }
  }
}
