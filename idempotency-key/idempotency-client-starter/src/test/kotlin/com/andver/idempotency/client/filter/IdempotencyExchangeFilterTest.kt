package com.andver.idempotency.client.filter

import com.andver.idempotency.api.IdempotencyHeaders
import com.andver.idempotency.client.IdempotencyClientAttributes
import com.andver.idempotency.client.IdempotencyClientProperties
import com.andver.idempotency.client.metrics.NoopIdempotencyClientMetrics
import com.andver.idempotency.client.store.OperationKeyStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import java.time.Duration

class IdempotencyExchangeFilterTest {

  private lateinit var server: MockWebServer

  @BeforeEach
  fun setUp() {
    server = MockWebServer()
    server.start()
  }

  @AfterEach
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `retries conflict with the same idempotency key`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(409)
        .addHeader("Retry-After", "0")
        .setBody("""{"error":"in progress"}"""),
    )
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("""{"ok":true}"""),
    )

    val filter = IdempotencyExchangeFilter(
      keyStore = OperationKeyStore { "stable-key" },
      properties = IdempotencyClientProperties(
        maxRetries = 3,
        defaultRetryDelay = Duration.ofMillis(10),
      ),
      metrics = NoopIdempotencyClientMetrics,
    )
    val client = WebClient.builder()
      .baseUrl(server.url("/").toString())
      .filter(filter)
      .build()

    StepVerifier.create(
      client.post()
        .uri("/charges")
        .attribute(IdempotencyClientAttributes.OPERATION_ID, "op-1")
        .attribute(IdempotencyClientAttributes.OWNER, "alice")
        .retrieve()
        .bodyToMono(String::class.java),
    )
      .expectNext("""{"ok":true}""")
      .verifyComplete()

    assertEquals("stable-key", server.takeRequest().getHeader(IdempotencyHeaders.KEY))
    assertEquals("stable-key", server.takeRequest().getHeader(IdempotencyHeaders.KEY))
  }
}
