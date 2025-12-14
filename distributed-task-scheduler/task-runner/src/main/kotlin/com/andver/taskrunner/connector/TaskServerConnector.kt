package com.andver.taskrunner.connector

import com.andver.taskrunner.model.RunTaskParams
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

interface ComponentConnector {
  fun runTask(component: String, params: RunTaskParams): Mono<Void>
}

@Component
class WebClientComponentConnector(
  private val webClient: WebClient,
) : ComponentConnector {

  override fun runTask(component: String, params: RunTaskParams): Mono<Void> {
    return webClient.post()
      .uri("http://$component:8080/scheduled-task/run")
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(params)
      .retrieve()
      .bodyToMono(Void::class.java)
  }
}