package com.andver.hash.router.routing

import com.andver.hash.router.HashRouterServiceApp
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@Tag("integration")
@SpringBootTest(
  classes = [HashRouterServiceApp::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWebTestClient
class RequestRouterIntegrationTest {
  @Autowired
  lateinit var webClient: WebTestClient

  companion object {
    private val backend1 = MockWebServer()
    private val backend2 = MockWebServer()

    @BeforeAll
    @JvmStatic
    fun setupServers() {
      backend1.dispatcher = BackendDispatcher("backend-1")
      backend2.dispatcher = BackendDispatcher("backend-2")
      backend1.start()
      backend2.start()
    }

    @AfterAll
    @JvmStatic
    fun shutdownServers() {
      backend1.shutdown()
      backend2.shutdown()
    }

    @JvmStatic
    @DynamicPropertySource
    fun registerProperties(registry: DynamicPropertyRegistry) {
      registry.add("router.virtual-nodes-per-node") { 64 }
      registry.add("router.health-check-enabled") { false }
      registry.add("router.nodes[0].id") { "backend-1" }
      registry.add("router.nodes[0].host") { backend1.hostName }
      registry.add("router.nodes[0].port") { backend1.port }
      registry.add("router.nodes[1].id") { "backend-2" }
      registry.add("router.nodes[1].host") { backend2.hostName }
      registry.add("router.nodes[1].port") { backend2.port }
    }
  }

  @Test
  fun `same routing key is always routed to same backend`() {
    val first = webClient.post()
      .uri("/route/42")
      .bodyValue("""{"amount":100}""")
      .exchange()
      .expectStatus().isOk
      .returnResult(String::class.java)
      .responseHeaders["X-Routed-To"]?.first()

    val second = webClient.post()
      .uri("/route/42")
      .bodyValue("""{"amount":100}""")
      .exchange()
      .expectStatus().isOk
      .returnResult(String::class.java)
      .responseHeaders["X-Routed-To"]?.first()

    org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second)
  }

  private class BackendDispatcher(
    private val backendId: String,
  ) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
      return when {
        request.path == "/health" -> MockResponse().setResponseCode(200).setBody("ok")
        request.path?.startsWith("/internal/route/") == true ->
          MockResponse().setResponseCode(200).setBody("""{"backendId":"$backendId"}""")
        else -> MockResponse().setResponseCode(404)
      }
    }
  }
}
