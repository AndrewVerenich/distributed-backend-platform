package com.andver.banking.command.handler

import com.andver.banking.command.BankingCommandApiApp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
@SpringBootTest(
  classes = [BankingCommandApiApp::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWebTestClient
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommandHandlerIntegrationTest {

  @Autowired
  lateinit var webClient: WebTestClient

  @Autowired
  lateinit var db: DatabaseClient

  companion object {
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
      .withDatabaseName("master")
      .withUsername("admin")
      .withPassword("admin")

    init {
      postgres.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun overrideProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.r2dbc.url") {
        "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
      }
      registry.add("spring.r2dbc.username") { postgres.username }
      registry.add("spring.r2dbc.password") { postgres.password }
    }
  }

  @BeforeEach
  fun createSchema() {
    db.sql(
      """
      CREATE TABLE IF NOT EXISTS event_store (
        id BIGSERIAL PRIMARY KEY,
        event_id UUID NOT NULL UNIQUE,
        aggregate_id BIGINT NOT NULL,
        aggregate_type VARCHAR(64) NOT NULL,
        event_type VARCHAR(64) NOT NULL,
        payload JSONB NOT NULL,
        version BIGINT NOT NULL,
        created_at TIMESTAMP NOT NULL
      );

      CREATE UNIQUE INDEX IF NOT EXISTS idx_event_store_aggregate_version
        ON event_store(aggregate_id, aggregate_type, version);

      DELETE FROM event_store;
      """
    ).then().block()
  }

  @Test
  fun `open account command creates event with version 1`() {
    webClient.post().uri("/api/v1/commands/open")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":1001,"owner":42}""")
      .exchange()
      .expectStatus().is2xxSuccessful

    val count = db.sql("SELECT COUNT(*) FROM event_store WHERE aggregate_id = 1001 AND version = 1")
      .map { row -> (row.get(0) as Number).toLong() }
      .one()
      .block()

    assert(count == 1L) { "Expected 1 event, got $count" }
  }

  @Test
  fun `duplicate open account is rejected`() {
    webClient.post().uri("/api/v1/commands/open")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":2001,"owner":1}""")
      .exchange()
      .expectStatus().is2xxSuccessful

    webClient.post().uri("/api/v1/commands/open")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":2001,"owner":1}""")
      .exchange()
      .expectStatus().is5xxServerError
  }

  @Test
  fun `deposit command creates event with incremented version`() {
    webClient.post().uri("/api/v1/commands/open")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":3001,"owner":1}""")
      .exchange()
      .expectStatus().is2xxSuccessful

    webClient.post().uri("/api/v1/commands/deposit")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":3001,"amount":500.00}""")
      .exchange()
      .expectStatus().is2xxSuccessful

    val maxVersion = db.sql("SELECT MAX(version) FROM event_store WHERE aggregate_id = 3001")
      .map { row -> (row.get(0) as Number).toLong() }
      .one()
      .block()

    assert(maxVersion == 2L) { "Expected version 2 after deposit, got $maxVersion" }
  }

  @Test
  fun `deposit on non-existent account is rejected`() {
    webClient.post().uri("/api/v1/commands/deposit")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":9999,"amount":100.00}""")
      .exchange()
      .expectStatus().is5xxServerError
  }

  @Test
  fun `multiple deposits increment version sequentially`() {
    webClient.post().uri("/api/v1/commands/open")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"accountId":4001,"owner":1}""")
      .exchange()
      .expectStatus().is2xxSuccessful

    repeat(3) {
      webClient.post().uri("/api/v1/commands/deposit")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""{"accountId":4001,"amount":100.00}""")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    val eventCount = db.sql("SELECT COUNT(*) FROM event_store WHERE aggregate_id = 4001")
      .map { row -> (row.get(0) as Number).toLong() }
      .one()
      .block()

    assert(eventCount == 4L) { "Expected 4 events (open + 3 deposits), got $eventCount" }
  }
}
