package com.andver.auth.service

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
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerIntegrationTest {

  @Autowired
  lateinit var webClient: WebTestClient

  @Autowired
  lateinit var db: DatabaseClient

  companion object {
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
      .withDatabaseName("auth_db")
      .withUsername("admin")
      .withPassword("admin")

    val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
      .withExposedPorts(6379)

    init {
      postgres.start()
      redis.start()
    }

    @JvmStatic
    @DynamicPropertySource
    fun overrideProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.r2dbc.url") {
        "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
      }
      registry.add("spring.r2dbc.username") { postgres.username }
      registry.add("spring.r2dbc.password") { postgres.password }
      registry.add("spring.data.redis.host") { redis.host }
      registry.add("spring.data.redis.port") { redis.firstMappedPort }
    }
  }

  @BeforeEach
  fun createSchema() {
    db.sql(
      """
      CREATE TABLE IF NOT EXISTS users (
        id BIGSERIAL PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password VARCHAR(255) NOT NULL,
        email VARCHAR(255) NOT NULL,
        roles VARCHAR(255) NOT NULL DEFAULT 'USER',
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
      );

      CREATE TABLE IF NOT EXISTS refresh_tokens (
        id BIGSERIAL PRIMARY KEY,
        token VARCHAR(255) NOT NULL UNIQUE,
        user_id BIGINT NOT NULL REFERENCES users(id),
        fingerprint VARCHAR(512) NOT NULL,
        family VARCHAR(255) NOT NULL,
        status VARCHAR(32) NOT NULL,
        expires_at TIMESTAMP NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT NOW()
      );

      DELETE FROM refresh_tokens;
      DELETE FROM users;
      """
    ).then().block()
  }

  @Test
  fun `full auth flow - register, login, validate, refresh, logout`() {
    // 1. Register
    webClient.post().uri("/auth/register")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"username":"integration_user","password":"pass123","email":"it@example.com"}""")
      .exchange()
      .expectStatus().isCreated
      .expectBody()
      .jsonPath("$.message").isEqualTo("User registered successfully")

    // 2. Login
    val loginResponse = webClient.post().uri("/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"username":"integration_user","password":"pass123"}""")
      .exchange()
      .expectStatus().isOk
      .expectCookie().exists("refreshToken")
      .expectBody()
      .jsonPath("$.accessToken").isNotEmpty
      .returnResult()

    val accessToken = extractJsonField(loginResponse.responseBody, "accessToken")
    val refreshTokenCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")
      ?.let { extractCookieValue(it, "refreshToken") }
      ?: error("Missing refreshToken cookie")

    // 3. Validate access token
    webClient.post().uri("/auth/validate")
      .header("Authorization", "Bearer $accessToken")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.valid").isEqualTo(true)
      .jsonPath("$.username").isEqualTo("integration_user")

    // 4. Refresh token rotation
    webClient.post().uri("/auth/refresh")
      .cookie("refreshToken", refreshTokenCookie)
      .exchange()
      .expectStatus().isOk
      .expectCookie().exists("refreshToken")
      .expectBody()
      .jsonPath("$.accessToken").isNotEmpty

    // 5. Logout
    webClient.post().uri("/auth/logout")
      .cookie("refreshToken", refreshTokenCookie)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.message").isEqualTo("Logged out successfully")
  }

  @Test
  fun `duplicate registration returns 400`() {
    val body = """{"username":"dup_user","password":"pass","email":"dup@example.com"}"""
    webClient.post().uri("/auth/register")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated

    webClient.post().uri("/auth/register")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `login with wrong password returns 401`() {
    webClient.post().uri("/auth/register")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"username":"user_x","password":"correctPass","email":"x@example.com"}""")
      .exchange()
      .expectStatus().isCreated

    webClient.post().uri("/auth/login")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""{"username":"user_x","password":"wrongPass"}""")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `refresh with invalid cookie returns 401`() {
    webClient.post().uri("/auth/refresh")
      .cookie("refreshToken", "non-existent-token")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `validate with malformed token returns valid=false`() {
    webClient.post().uri("/auth/validate")
      .header("Authorization", "Bearer not.a.valid.jwt")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.valid").isEqualTo(false)
  }

  private fun extractJsonField(body: ByteArray?, field: String): String {
    val json = body?.decodeToString() ?: ""
    val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex()
    return regex.find(json)?.groupValues?.get(1) ?: error("Field $field not found in $json")
  }

  private fun extractCookieValue(headerValue: String, name: String): String {
    return headerValue.split(";").firstOrNull()
      ?.trim()?.removePrefix("$name=")
      ?: error("Cookie $name not found in $headerValue")
  }
}
