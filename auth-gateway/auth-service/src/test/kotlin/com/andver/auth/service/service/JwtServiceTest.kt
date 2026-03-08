package com.andver.auth.service.service

import com.andver.auth.service.entity.User
import com.andver.auth.service.properties.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class JwtServiceTest {

  private lateinit var jwtService: JwtService
  private val secret = "this-is-a-very-long-secret-key-for-testing-purposes-at-least-32-bytes"
  private val properties = JwtProperties(
    secret = secret,
    accessExpiration = Duration.ofMinutes(15),
    refreshExpiration = Duration.ofDays(7),
  )

  @BeforeEach
  fun setUp() {
    jwtService = JwtService(properties)
  }

  @Test
  fun `generateAccessToken creates valid signed JWT with expected claims`() {
    val user = buildUser(id = 42L, username = "alice", roles = "USER,ADMIN")

    val token = jwtService.generateAccessToken(user)

    assertThat(token).isNotBlank()

    val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val claims = Jwts.parser().verifyWith(secretKey).build()
      .parseSignedClaims(token).payload

    assertThat(claims.subject).isEqualTo("42")
    assertThat(claims["username"] as String).isEqualTo("alice")
    @Suppress("UNCHECKED_CAST")
    assertThat(claims["roles"] as List<String>).containsExactlyInAnyOrder("USER", "ADMIN")
    assertThat(claims["jti"]).isNotNull()
    assertThat(claims.expiration).isAfter(Date())
  }

  @Test
  fun `generateAccessToken sets expiration equal to accessExpiration property`() {
    val user = buildUser(id = 1L)
    val before = System.currentTimeMillis()

    val token = jwtService.generateAccessToken(user)

    val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val expiration = Jwts.parser().verifyWith(secretKey).build()
      .parseSignedClaims(token).payload.expiration

    val expectedExpirationApprox = before + properties.accessExpiration.toMillis()
    assertThat(expiration.time).isBetween(expectedExpirationApprox - 2000, expectedExpirationApprox + 2000)
  }

  @Test
  fun `generateRefreshToken produces unique UUID values`() {
    val token1 = jwtService.generateRefreshToken()
    val token2 = jwtService.generateRefreshToken()

    assertThat(token1).isNotBlank()
    assertThat(token2).isNotBlank()
    assertThat(token1).isNotEqualTo(token2)
    assertThatCode { UUID.fromString(token1) }.doesNotThrowAnyException()
    assertThatCode { UUID.fromString(token2) }.doesNotThrowAnyException()
  }

  @Test
  fun `extractClaims returns claims for a valid token`() {
    val user = buildUser(id = 7L, username = "bob")
    val token = jwtService.generateAccessToken(user)

    StepVerifier.create(jwtService.extractClaims(token))
      .assertNext { claims ->
        assertThat(claims.subject).isEqualTo("7")
        assertThat(claims["username"]).isEqualTo("bob")
      }
      .verifyComplete()
  }

  @Test
  fun `extractClaims emits error for tampered token`() {
    val user = buildUser(id = 1L)
    val token = jwtService.generateAccessToken(user)
    val tampered = token.dropLast(5) + "XXXXX"

    StepVerifier.create(jwtService.extractClaims(tampered))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Invalid token" }
      .verify()
  }

  @Test
  fun `extractClaims emits error for token signed with different key`() {
    val differentSecret = "another-totally-different-secret-key-also-32-bytes-long!!"
    val otherProperties = JwtProperties(differentSecret, Duration.ofMinutes(15), Duration.ofDays(7))
    val otherService = JwtService(otherProperties)
    val tokenFromOtherService = otherService.generateAccessToken(buildUser(id = 1L))

    StepVerifier.create(jwtService.extractClaims(tokenFromOtherService))
      .expectErrorMatches { it is IllegalArgumentException }
      .verify()
  }

  @Test
  fun `extractClaims emits error for expired token`() {
    val expiredProperties = JwtProperties(secret, Duration.ofMillis(1), Duration.ofDays(7))
    val expiredService = JwtService(expiredProperties)
    val token = expiredService.generateAccessToken(buildUser(id = 1L))

    StepVerifier.create(jwtService.extractClaims(token))
      .expectErrorMatches { it is IllegalArgumentException }
      .verify()
  }

  @Test
  fun `generateAccessToken embeds unique jti per token`() {
    val user = buildUser(id = 1L)
    val token1 = jwtService.generateAccessToken(user)
    val token2 = jwtService.generateAccessToken(user)

    val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    val parser = Jwts.parser().verifyWith(secretKey).build()
    val jti1 = parser.parseSignedClaims(token1).payload["jti"]
    val jti2 = parser.parseSignedClaims(token2).payload["jti"]

    assertThat(jti1).isNotEqualTo(jti2)
  }

  private fun buildUser(
    id: Long = 1L,
    username: String = "testuser",
    roles: String = "USER",
  ) = User(
    id = id,
    username = username,
    password = "hashed",
    email = "$username@example.com",
    roles = roles,
    createdAt = LocalDateTime.now(),
  )
}
