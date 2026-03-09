package com.andver.counter.controller

import com.andver.counter.CounterServiceApp
import com.andver.counter.consumer.VideoViewConsumer
import com.andver.counter.consumer.VideoViewCountConsumer
import com.andver.counter.scheduler.FlushScheduler
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

@Tag("integration")
@Testcontainers
@SpringBootTest(
  classes = [CounterServiceApp::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWebTestClient
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CounterControllerIntegrationTest {

  @Autowired lateinit var webClient: WebTestClient
  @Autowired lateinit var db: DatabaseClient

  @MockkBean(relaxed = true) lateinit var videoViewCountConsumer: VideoViewCountConsumer
  @MockkBean(relaxed = true) lateinit var videoViewConsumer: VideoViewConsumer
  @MockkBean(relaxed = true) lateinit var flushScheduler: FlushScheduler

  companion object {
    val redis: GenericContainer<*> = GenericContainer("redis:7.2-alpine")
      .withExposedPorts(6379)

    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
      .withDatabaseName("test")
      .withUsername("test")
      .withPassword("test")

    init {
      redis.start()
      postgres.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun overrideProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.data.redis.host") { redis.host }
      registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
      registry.add("spring.r2dbc.url") {
        "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/test"
      }
      registry.add("spring.r2dbc.username") { postgres.username }
      registry.add("spring.r2dbc.password") { postgres.password }
    }
  }

  @BeforeEach
  fun createSchema() {
    db.sql(
      """
      CREATE TABLE IF NOT EXISTS video_view_counts (
        video_id BIGINT PRIMARY KEY,
        total_views BIGINT NOT NULL DEFAULT 0,
        unique_viewers_estimate BIGINT NOT NULL DEFAULT 0,
        last_updated TIMESTAMP NOT NULL
      )
      """.trimIndent()
    ).then().block()
  }

  @Test
  fun `recording views from different users increments both total and unique counters`() {
    webClient.post().uri("/counters/1001/view?userId=10").exchange().expectStatus().isOk
    webClient.post().uri("/counters/1001/view?userId=20").exchange().expectStatus().isOk
    webClient.post().uri("/counters/1001/view?userId=30").exchange().expectStatus().isOk

    webClient.get().uri("/counters/1001")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.videoId").isEqualTo(1001)
      .jsonPath("$.totalViews").isEqualTo(3)
      .jsonPath("$.uniqueViewers").isEqualTo(3)
  }

  @Test
  fun `same user viewing multiple times counts as one unique viewer`() {
    webClient.post().uri("/counters/2001/view?userId=5").exchange().expectStatus().isOk
    webClient.post().uri("/counters/2001/view?userId=5").exchange().expectStatus().isOk
    webClient.post().uri("/counters/2001/view?userId=5").exchange().expectStatus().isOk

    webClient.get().uri("/counters/2001")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.totalViews").isEqualTo(3)
      .jsonPath("$.uniqueViewers").isEqualTo(1)
  }

  @Test
  fun `unknown video returns zero counts`() {
    webClient.get().uri("/counters/99999")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.totalViews").isEqualTo(0)
      .jsonPath("$.uniqueViewers").isEqualTo(0)
  }

  @Test
  fun `mixed unique and repeat views are counted correctly`() {
    webClient.post().uri("/counters/3001/view?userId=1").exchange().expectStatus().isOk
    webClient.post().uri("/counters/3001/view?userId=1").exchange().expectStatus().isOk
    webClient.post().uri("/counters/3001/view?userId=2").exchange().expectStatus().isOk

    webClient.get().uri("/counters/3001")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.totalViews").isEqualTo(3)
      .jsonPath("$.uniqueViewers").isEqualTo(2)
  }
}
