package com.andver.taskrunner.connector

import com.andver.taskrunner.model.RunTaskParams
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

interface ComponentConnector {
  fun runTask(component: String, params: RunTaskParams): Mono<Void>
}

@Component
class WebClientComponentConnector(
  private val webClient: WebClient,
) : ComponentConnector {

  private val log = LoggerFactory.getLogger(WebClientComponentConnector::class.java)

  override fun runTask(component: String, params: RunTaskParams): Mono<Void> {
    return webClient.post()
      .uri("http://$component:8080/scheduled-task/run")
      .contentType(MediaType.APPLICATION_JSON)
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(params)
      .retrieve()
      .bodyToMono(Void::class.java)
      .retryWhen(
        Retry.backoff(3, Duration.ofSeconds(1))
          .filter { throwable ->
            when (throwable) {
              is WebClientResponseException -> {
                val statusCode = throwable.statusCode
                statusCode.is5xxServerError || statusCode == HttpStatus.REQUEST_TIMEOUT
              }

              else -> true
            }
          }
          .doBeforeRetry { retrySignal ->
            log.warn(
              "Retrying request to $component. Attempt: ${retrySignal.totalRetries() + 1}/${retrySignal.totalRetriesInARow() + 1}",
              retrySignal.failure()
            )
          }
          .onRetryExhaustedThrow { retryBackoffSpec, retrySignal ->
            log.error(
              "All retry attempts exhausted for $component",
              retrySignal.failure()
            )
            retrySignal.failure()
          }
      )
  }
}