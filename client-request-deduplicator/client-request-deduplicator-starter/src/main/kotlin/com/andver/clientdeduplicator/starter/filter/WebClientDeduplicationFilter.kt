package com.andver.clientdeduplicator.starter.filter

import com.andver.clientdeduplicator.starter.body.X_CAPTURED_BODY_HEADER
import com.andver.clientdeduplicator.starter.cache.CacheClient
import com.andver.clientdeduplicator.starter.cache.CacheRuleMatcher
import com.andver.clientdeduplicator.starter.fingerprint.FingerprintGenerator
import com.andver.clientdeduplicator.starter.metrics.DeduplicatorMetrics
import com.andver.clientdeduplicator.starter.properties.CacheProperties
import com.andver.clientdeduplicator.starter.properties.CacheRule
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono

private const val X_CACHE_HEADER = "X-Cache"
private const val HIT = "HIT"
private const val MISS = "MISS"
private const val BYPASS = "BYPASS"

class WebClientDeduplicationFilter(
  private val fingerprintGenerator: FingerprintGenerator,
  private val cacheClient: CacheClient,
  private val cacheProperties: CacheProperties,
  private val cacheRuleMatcher: CacheRuleMatcher,
  private val deduplicatorMetrics: DeduplicatorMetrics,
) : ExchangeFilterFunction {

  private val log = LoggerFactory.getLogger(WebClientDeduplicationFilter::class.java)

  override fun filter(
    request: ClientRequest,
    next: ExchangeFunction
  ): Mono<ClientResponse> {
    if (!cacheProperties.enabled) return next.exchange(removeCapturedHeader(request))

    val method = request.method().name()
    val rule =
      cacheRuleMatcher.findRule(method, request.url().path) ?: return next.exchange(removeCapturedHeader(request))
    val uri = request.url().toString()
    val body = request.headers().getFirst(X_CAPTURED_BODY_HEADER)
    val fingerprint = fingerprintGenerator.generate(
      method = method,
      uri = uri,
      body = body,
      excludeFields = rule.excludeFields,
      excludeQueryParams = rule.excludeQueryParams
    )
    log.info("Fingerprint=$fingerprint for $method $uri $body")

    return cacheClient.get(fingerprint)
      .map { cachedBody ->
        log.info("Cache hit for $method $uri $body")
        deduplicatorMetrics.hit(method, uri)
        ClientResponse.create(HttpStatus.OK)
          .header(X_CACHE_HEADER, HIT)
          .body(cachedBody!!)
          .build()
      }
      .switchIfEmpty {
        exchangeAndCache(next, request, method, uri, body, fingerprint, rule)
      }
  }

  private fun exchangeAndCache(
    next: ExchangeFunction,
    request: ClientRequest,
    method: String,
    uri: String,
    body: String?,
    fingerprint: String,
    rule: CacheRule
  ): Mono<ClientResponse> {
    return next.exchange(removeCapturedHeader(request))
      .flatMap { response ->
        val status = response.statusCode()
        response.bodyToMono(String::class.java)
          .defaultIfEmpty("")
          .flatMap { responseBody ->
            if (status.is2xxSuccessful) {
              log.info("Cache miss for $method $uri $body")
              deduplicatorMetrics.miss(method, uri)
              cacheClient.set(fingerprint, responseBody, rule.ttl)
                .thenReturn(
                  ClientResponse.create(HttpStatus.OK)
                    .header(X_CACHE_HEADER, MISS)
                    .body(responseBody)
                    .build()
                )
            } else {
              log.info("Cache bypass for $method $uri $body")
              deduplicatorMetrics.bypass(method, uri)
              ClientResponse.create(status)
                .headers { it.addAll(response.headers().asHttpHeaders()) }
                .header(X_CACHE_HEADER, BYPASS)
                .body(responseBody)
                .build().toMono()
            }
          }
      }
  }

  private fun removeCapturedHeader(request: ClientRequest): ClientRequest {
    return ClientRequest.from(request).headers { it.remove(X_CAPTURED_BODY_HEADER) }.build()
  }
}
