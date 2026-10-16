package com.andver.webhook.worker

import com.andver.webhook.core.DeliveryOutcome
import com.andver.webhook.core.HmacSigner
import com.andver.webhook.core.RetryPolicy
import com.andver.webhook.core.SsrfGuard
import com.andver.webhook.core.Topics
import com.andver.webhook.core.WebhookStatus
import com.andver.webhook.store.DeliveryAttempt
import com.andver.webhook.store.DeliveryAttemptRepository
import com.andver.webhook.store.WebhookRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class DeliveryService(
  private val attempts: DeliveryAttemptRepository,
  private val webhooks: WebhookRepository,
  private val webClient: WebClient,
  private val retryPolicy: RetryPolicy,
  private val props: WorkerProperties,
  private val objectMapper: ObjectMapper,
  private val metrics: DeliveryMetrics,
  private val kafkaTemplate: KafkaTemplate<String, String>,
) {
  private val log = LoggerFactory.getLogger(javaClass)

  fun claimAndDeliver(): Mono<Boolean> =
    attempts.claimOne(props.maxInFlightPerWebhook)
      .flatMap { attempt -> deliver(attempt).thenReturn(true) }
      .defaultIfEmpty(false)

  fun reclaimStale(): Mono<Long> = attempts.reclaimStale(props.leaseSeconds)

  private fun deliver(attempt: DeliveryAttempt): Mono<Void> =
    webhooks.findById(attempt.webhookId)
      .flatMap { webhook ->
        if (webhook.status != WebhookStatus.active) {
          return@flatMap attempts.markRetry(
            attempt.id,
            attempt.attemptCount,
            Instant.now().plusSeconds(5),
            "webhook paused",
          ).then()
        }
        val urlCheck = SsrfGuard.validate(webhook.url, props.allowPrivateUrls)
        if (urlCheck.isFailure) {
          return@flatMap toDlq(attempt, urlCheck.exceptionOrNull()?.message ?: "ssrf blocked")
        }
        val timestamp = Instant.now().epochSecond.toString()
        val outboundBody = objectMapper.createObjectNode().apply {
          put("eventType", attempt.eventType)
          put("eventId", attempt.eventId)
          set<ObjectNode>("body", attempt.body as? ObjectNode ?: objectMapper.valueToTree(attempt.body))
        }
        val bodyJson = objectMapper.writeValueAsString(outboundBody)
        val signature = HmacSigner.sign(webhook.signingSecret, timestamp, bodyJson)
        val started = System.currentTimeMillis()

        webClient.post()
          .uri(webhook.url)
          .contentType(MediaType.APPLICATION_JSON)
          .header("X-Signature", signature)
          .header("X-Idempotency-Key", attempt.idempotencyKey)
          .header("X-Timestamp", timestamp)
          .bodyValue(bodyJson)
          .retrieve()
          .toBodilessEntity()
          .map<DeliveryOutcome> { DeliveryOutcome.Success }
          .onErrorResume { err -> Mono.just(toOutcome(err)) }
          .flatMap { outcome ->
            metrics.recordLatency(System.currentTimeMillis() - started)
            when (outcome) {
              is DeliveryOutcome.Success -> onSuccess(attempt, webhook.id)
              is DeliveryOutcome.Retryable -> onRetryable(attempt, webhook.id, outcome.reason)
              is DeliveryOutcome.NonRetryable -> onNonRetryable(attempt, webhook.id, outcome)
            }
          }
      }
      .then()

  private fun onSuccess(attempt: DeliveryAttempt, webhookId: String): Mono<Void> =
    attempts.markSucceeded(attempt.id)
      .then(webhooks.resetTransportFailures(webhookId))
      .doOnSuccess {
        metrics.succeeded()
        log.info("delivered id={} eventId={} webhookId={}", attempt.id, attempt.eventId, webhookId)
      }
      .then()

  private fun onRetryable(attempt: DeliveryAttempt, webhookId: String, reason: String): Mono<Void> {
    val nextCount = attempt.attemptCount + 1
    return webhooks.incrementTransportFailure(webhookId, props.pauseAfterTransportFailures)
      .flatMap { webhook ->
        if (webhook.status == WebhookStatus.paused) {
          metrics.paused()
          log.warn("endpoint paused webhookId={} failures={}", webhookId, webhook.consecutiveTransportFailures)
        }
        val nextAt = retryPolicy.nextAttemptAt(nextCount)
        if (nextAt == null || retryPolicy.exhausted(nextCount)) {
          toDlq(attempt.copy(attemptCount = nextCount), "max attempts: $reason")
        } else {
          metrics.retry()
          attempts.markRetry(attempt.id, nextCount, nextAt, reason).then()
        }
      }
  }

  private fun onNonRetryable(
    attempt: DeliveryAttempt,
    webhookId: String,
    outcome: DeliveryOutcome.NonRetryable,
  ): Mono<Void> {
    val pauseMono =
      if (outcome.statusCode == 410) {
        webhooks.pause(webhookId).doOnSuccess {
          metrics.paused()
          log.warn("endpoint paused after 410 webhookId={}", webhookId)
        }.then()
      } else {
        Mono.empty()
      }
    return pauseMono.then(toDlq(attempt, outcome.reason))
  }

  private fun toDlq(attempt: DeliveryAttempt, reason: String): Mono<Void> {
    metrics.dlq()
    val count = if (attempt.attemptCount == 0) 1 else attempt.attemptCount
    return attempts.markDlq(attempt.id, count, reason)
      .then(
        Mono.fromFuture(
          kafkaTemplate.send(
            Topics.DLQ,
            attempt.webhookId,
            objectMapper.writeValueAsString(
              mapOf(
                "deliveryId" to attempt.id,
                "eventId" to attempt.eventId,
                "webhookId" to attempt.webhookId,
                "reason" to reason,
              ),
            ),
          ).toCompletableFuture(),
        ),
      )
      .doOnSuccess { log.warn("dlq id={} reason={}", attempt.id, reason) }
      .then()
  }

  private fun toOutcome(err: Throwable): DeliveryOutcome =
    when (err) {
      is WebClientResponseException -> retryPolicy.classifyHttp(err.statusCode.value(), null)
      is WebClientRequestException -> DeliveryOutcome.Retryable(err.message ?: "network")
      else -> DeliveryOutcome.Retryable(err.message ?: err.javaClass.simpleName)
    }
}
