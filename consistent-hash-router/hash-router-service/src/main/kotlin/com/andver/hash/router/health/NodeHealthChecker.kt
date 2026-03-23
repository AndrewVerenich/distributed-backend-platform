package com.andver.hash.router.health

import com.andver.hash.router.config.RouterProperties
import com.andver.hash.router.node.NodeRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class NodeHealthChecker(
  private val nodeRegistry: NodeRegistry,
  private val routerProperties: RouterProperties,
  private val webClientBuilder: WebClient.Builder,
) {
  @Scheduled(fixedDelayString = "\${router.health-check-delay-ms:5000}")
  fun checkNodes() {
    if (!routerProperties.healthCheckEnabled) {
      return
    }

    nodeRegistry.allNodes().forEach { node ->
      webClientBuilder.build()
        .get()
        .uri("${node.baseUrl()}/health")
        .retrieve()
        .toBodilessEntity()
        .timeout(Duration.ofMillis(routerProperties.healthCheckTimeoutMs))
        .doOnSuccess { nodeRegistry.markHealthy(node.id) }
        .doOnError { nodeRegistry.markFailure(node.id) }
        .subscribe({}, { })
    }
  }
}
