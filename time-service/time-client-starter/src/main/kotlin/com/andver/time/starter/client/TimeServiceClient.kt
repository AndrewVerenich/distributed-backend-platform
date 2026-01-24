package com.andver.time.starter.client

import com.andver.time.starter.model.SyncRequest
import com.andver.time.starter.model.SyncResponse
import com.andver.time.starter.model.TimeResponse
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

interface TimeServiceClient {
  fun getCurrentTime(): Mono<TimeResponse>
  fun startSync(nodeId: String, localTime: Long): Mono<Long>
  fun completeSync(nodeId: String, clientT0: Long, clientT3: Long): Mono<SyncResponse>
}

class DefaultTimeServiceClient(
  private val webClient: WebClient,
) : TimeServiceClient {

  private val logger = LoggerFactory.getLogger(DefaultTimeServiceClient::class.java)

  override fun getCurrentTime(): Mono<TimeResponse> {
    return webClient.get()
      .uri("/time/now")
      .retrieve()
      .bodyToMono(TimeResponse::class.java)
      .doOnError { logger.error("Failed to get current time", it) }
  }

  override fun startSync(nodeId: String, localTime: Long): Mono<Long> {
    return webClient.post()
      .uri("/time/sync/start")
      .bodyValue(
        SyncRequest(
          nodeId = nodeId,
          localTime = localTime
        )
      )
      .retrieve()
      .bodyToMono(Long::class.java)
      .doOnError { logger.error("Failed to start sync", it) }
  }

  override fun completeSync(nodeId: String, clientT0: Long, clientT3: Long): Mono<SyncResponse> {
    return webClient.post()
      .uri { uriBuilder ->
        uriBuilder.path("/time/sync/complete")
          .queryParam("nodeId", nodeId)
          .queryParam("clientT0", clientT0)
          .queryParam("clientT3", clientT3)
          .build()
      }
      .retrieve()
      .bodyToMono(SyncResponse::class.java)
      .doOnError { logger.error("Failed to complete sync", it) }
  }
}

