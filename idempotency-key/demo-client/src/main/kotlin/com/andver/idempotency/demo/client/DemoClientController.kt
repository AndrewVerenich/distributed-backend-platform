package com.andver.idempotency.demo.client

import com.andver.idempotency.api.IdempotencyHeaders
import com.andver.idempotency.client.idempotent
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RestController
class DemoClientController(
  private val chargeWebClient: WebClient,
) {

  @PostMapping("/demo/charge")
  fun charge(
    @RequestBody request: DemoChargeRequest,
    @RequestParam(defaultValue = "starter") mode: String,
    @RequestParam(defaultValue = "0") delayMs: Long,
  ): Mono<Map<String, Any?>> {
    val attempts = AtomicInteger()
    val owner = request.ownerId
    val body = mapOf("amount" to request.amount)

    val call: Mono<RemoteChargeResult> = when (mode.lowercase()) {
      "naive" -> naiveCharge(owner, body, delayMs, attempts)
      else -> starterCharge(request.operationId, owner, body, delayMs, attempts)
    }

    return call
      .onErrorResume { error ->
        Mono.just(
          RemoteChargeResult(
            status = 504,
            body = """{"error":"${error.javaClass.simpleName}: ${error.message?.replace("\"", "'")}"}""",
            replayed = false,
          ),
        )
      }
      .map { result ->
        mapOf(
          "mode" to mode.lowercase(),
          "operationId" to request.operationId,
          "attempts" to attempts.get(),
          "status" to result.status,
          "replayed" to result.replayed,
          "body" to result.body,
        )
      }
  }

  private fun starterCharge(
    operationId: String,
    owner: String,
    body: Map<String, Long>,
    delayMs: Long,
    attempts: AtomicInteger,
  ): Mono<RemoteChargeResult> =
    chargeWebClient.post()
      .uri { it.path("/charges").queryParam("delayMs", delayMs).build() }
      .contentType(MediaType.APPLICATION_JSON)
      .idempotent(operationId, owner)
      .bodyValue(body)
      .exchangeToMono { response ->
        attempts.incrementAndGet()
        response.bodyToMono(String::class.java).defaultIfEmpty("").map { payload ->
          RemoteChargeResult(
            status = response.statusCode().value(),
            body = payload,
            replayed = response.headers().asHttpHeaders().getFirst(IdempotencyHeaders.REPLAYED) == "true",
          )
        }
      }

  private fun naiveCharge(
    owner: String,
    body: Map<String, Long>,
    delayMs: Long,
    attempts: AtomicInteger,
  ): Mono<RemoteChargeResult> =
    Mono.defer {
      attempts.incrementAndGet()
      val key = UUID.randomUUID().toString()
      chargeWebClient.post()
        .uri { it.path("/charges").queryParam("delayMs", delayMs).build() }
        .contentType(MediaType.APPLICATION_JSON)
        .header(IdempotencyHeaders.KEY, key)
        .header(IdempotencyHeaders.OWNER, owner)
        .bodyValue(body)
        .exchangeToMono { response ->
          response.bodyToMono(String::class.java).defaultIfEmpty("").map { payload ->
            RemoteChargeResult(
              status = response.statusCode().value(),
              body = payload,
              replayed = response.headers().asHttpHeaders().getFirst(IdempotencyHeaders.REPLAYED) == "true",
            )
          }
        }
    }.retryWhen(
      Retry.fixedDelay(3, Duration.ofMillis(200))
        .filter { error ->
          error !is WebClientResponseException || error.statusCode.value() == 409
        },
    )
}

data class DemoChargeRequest(
  val operationId: String,
  val ownerId: String = "alice",
  val amount: Long = 10,
)

private data class RemoteChargeResult(
  val status: Int,
  val body: String,
  val replayed: Boolean,
)
