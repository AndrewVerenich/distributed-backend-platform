package com.andver.auth.service.service

import com.andver.auth.service.entity.RefreshToken
import com.andver.auth.service.entity.RefreshTokenStatus
import com.andver.auth.service.entity.User
import com.andver.auth.service.properties.JwtProperties
import com.andver.auth.service.repository.RefreshTokenRepository
import com.andver.auth.service.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.LocalDateTime

class AuthServiceTest {

  private val userRepository = mockk<UserRepository>()
  private val refreshTokenRepository = mockk<RefreshTokenRepository>()
  private val redisTemplate = mockk<ReactiveRedisTemplate<String, String>>()
  private val redisValueOps = mockk<ReactiveValueOperations<String, String>>()
  private val passwordEncoder = BCryptPasswordEncoder()
  private val jwtProperties = JwtProperties(
    secret = "test-secret-key-at-least-32-bytes-long!!!!!!!!!!!!!",
    accessExpiration = Duration.ofMinutes(15),
    refreshExpiration = Duration.ofDays(7),
  )
  private lateinit var jwtService: JwtService
  private lateinit var authService: AuthService

  @BeforeEach
  fun setUp() {
    jwtService = JwtService(jwtProperties)
    authService = AuthService(
      jwtProperties = jwtProperties,
      userRepository = userRepository,
      refreshTokenRepository = refreshTokenRepository,
      passwordEncoder = passwordEncoder,
      jwtService = jwtService,
      redisTemplate = redisTemplate,
    )
  }

  @Test
  fun `register saves new user with encoded password`() {
    every { userRepository.findByUsername("alice") } returns Mono.empty()
    every { userRepository.save(any()) } answers {
      val saved = firstArg<User>()
      Mono.just(saved.copy(id = 1L))
    }

    StepVerifier.create(authService.register("alice", "secret123", "alice@example.com"))
      .assertNext { user ->
        assert(user.username == "alice")
        assert(user.email == "alice@example.com")
        assert(passwordEncoder.matches("secret123", user.password))
        assert(user.roles == "USER")
      }
      .verifyComplete()
  }

  @Test
  fun `register rejects duplicate username`() {
    every { userRepository.findByUsername("alice") } returns Mono.just(buildUser("alice"))
    every { userRepository.save(any()) } returns Mono.never()

    StepVerifier.create(authService.register("alice", "pass", "new@example.com"))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Username already exists" }
      .verify()
  }

  @Test
  fun `login returns token pair for valid credentials`() {
    val rawPassword = "correctPassword"
    val user = buildUser("bob", passwordEncoder.encode(rawPassword))
    every { userRepository.findByUsername("bob") } returns Mono.just(user)
    every { refreshTokenRepository.save(any()) } answers { Mono.just(firstArg<RefreshToken>().copy(id = 10L)) }

    StepVerifier.create(authService.login("bob", rawPassword, "Mozilla|127.0.0.1"))
      .assertNext { pair ->
        assert(pair.accessToken.isNotBlank())
        assert(pair.refreshToken.isNotBlank())
      }
      .verifyComplete()
  }

  @Test
  fun `login rejects unknown username`() {
    every { userRepository.findByUsername("unknown") } returns Mono.empty()

    StepVerifier.create(authService.login("unknown", "pass", "fp"))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Invalid credentials" }
      .verify()
  }

  @Test
  fun `login rejects wrong password`() {
    val user = buildUser("carol", passwordEncoder.encode("realPassword"))
    every { userRepository.findByUsername("carol") } returns Mono.just(user)

    StepVerifier.create(authService.login("carol", "wrongPassword", "fp"))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Invalid credentials" }
      .verify()
  }

  @Test
  fun `refresh rotates token pair for valid active token`() {
    val user = buildUser("dave", id = 5L)
    val activeToken = buildRefreshToken(userId = 5L, status = RefreshTokenStatus.ACTIVE)

    every { refreshTokenRepository.findByToken("old-token-uuid") } returns Mono.just(activeToken)
    every { userRepository.findById(5L) } returns Mono.just(user)
    every { refreshTokenRepository.updateStatus(activeToken.id!!, RefreshTokenStatus.USED) } returns Mono.just(1)
    every { refreshTokenRepository.save(any()) } answers { Mono.just(firstArg<RefreshToken>().copy(id = 99L)) }

    StepVerifier.create(authService.refresh("old-token-uuid", activeToken.fingerprint))
      .assertNext { result ->
        assert(result.accessToken.isNotBlank())
        assert(result.newRefreshToken.isNotBlank())
        assert(result.newRefreshToken != "old-token-uuid")
      }
      .verifyComplete()
  }

  @Test
  fun `refresh rejects non-existent token`() {
    every { refreshTokenRepository.findByToken("ghost") } returns Mono.empty()

    StepVerifier.create(authService.refresh("ghost", "any-fp"))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Invalid refresh token" }
      .verify()
  }

  @Test
  fun `refresh rejects already-used token and revokes entire family (theft detection)`() {
    val usedToken = buildRefreshToken(userId = 3L, status = RefreshTokenStatus.USED)
    every { refreshTokenRepository.findByToken("used-token") } returns Mono.just(usedToken)
    every {
      refreshTokenRepository.revokeFamily(3L, usedToken.family)
    } returns Mono.just(2)

    StepVerifier.create(authService.refresh("used-token", usedToken.fingerprint))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Refresh token revoked" }
      .verify()

    verify { refreshTokenRepository.revokeFamily(3L, usedToken.family) }
  }

  @Test
  fun `refresh rejects revoked token and triggers family revocation`() {
    val revokedToken = buildRefreshToken(userId = 4L, status = RefreshTokenStatus.REVOKED)
    every { refreshTokenRepository.findByToken("revoked-token") } returns Mono.just(revokedToken)
    every {
      refreshTokenRepository.revokeFamily(4L, revokedToken.family)
    } returns Mono.just(1)

    StepVerifier.create(authService.refresh("revoked-token", revokedToken.fingerprint))
      .expectErrorMatches { it is IllegalArgumentException }
      .verify()
  }

  @Test
  fun `refresh rejects expired token`() {
    val expiredToken = buildRefreshToken(
      userId = 5L,
      status = RefreshTokenStatus.ACTIVE,
      expiresAt = LocalDateTime.now().minusHours(1),
    )
    every { refreshTokenRepository.findByToken("expired-token") } returns Mono.just(expiredToken)

    StepVerifier.create(authService.refresh("expired-token", expiredToken.fingerprint))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Refresh token expired" }
      .verify()
  }

  @Test
  fun `refresh detects fingerprint mismatch and revokes token family`() {
    val token = buildRefreshToken(userId = 6L, status = RefreshTokenStatus.ACTIVE, fingerprint = "original-fp")
    every { refreshTokenRepository.findByToken("token") } returns Mono.just(token)
    every {
      refreshTokenRepository.revokeFamily(6L, token.family)
    } returns Mono.just(1)

    StepVerifier.create(authService.refresh("token", "attacker-fp"))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Security violation detected" }
      .verify()

    verify { refreshTokenRepository.revokeFamily(6L, token.family) }
  }

  @Test
  fun `logout revokes the provided refresh token`() {
    val token = buildRefreshToken(userId = 1L, status = RefreshTokenStatus.ACTIVE)
    every { refreshTokenRepository.findByToken("my-refresh-token") } returns Mono.just(token)
    every { refreshTokenRepository.updateStatus(token.id!!, RefreshTokenStatus.REVOKED) } returns Mono.just(1)

    StepVerifier.create(authService.logout("my-refresh-token"))
      .verifyComplete()

    verify { refreshTokenRepository.updateStatus(token.id!!, RefreshTokenStatus.REVOKED) }
  }

  @Test
  fun `logout completes silently when token not found`() {
    every { refreshTokenRepository.findByToken("non-existent") } returns Mono.empty()

    StepVerifier.create(authService.logout("non-existent"))
      .verifyComplete()
  }

  @Test
  fun `logoutAll revokes all tokens for the user extracted from JWT`() {
    val user = buildUser("eve", id = 10L)
    val accessToken = jwtService.generateAccessToken(user)
    every { refreshTokenRepository.revokeAllForUser(10L) } returns Mono.just(3)

    StepVerifier.create(authService.logoutAll(accessToken))
      .verifyComplete()

    verify { refreshTokenRepository.revokeAllForUser(10L) }
  }

  @Test
  fun `validate returns claims when token is valid and not blacklisted`() {
    val user = buildUser("frank", id = 20L)
    val accessToken = jwtService.generateAccessToken(user)
    every { redisTemplate.hasKey(match<String> { it.startsWith("blacklist:") }) } returns Mono.just(false)

    StepVerifier.create(authService.validate(accessToken))
      .assertNext { claims ->
        assert(claims.subject == "20")
      }
      .verifyComplete()
  }

  @Test
  fun `validate rejects blacklisted token`() {
    val user = buildUser("grace", id = 21L)
    val accessToken = jwtService.generateAccessToken(user)
    every { redisTemplate.hasKey(match<String> { it.startsWith("blacklist:") }) } returns Mono.just(true)

    StepVerifier.create(authService.validate(accessToken))
      .expectErrorMatches { it is IllegalArgumentException && it.message == "Token is blacklisted" }
      .verify()
  }

  private fun buildUser(
    username: String = "user",
    password: String = "hashed",
    id: Long = 1L,
  ) = User(
    id = id,
    username = username,
    password = password,
    email = "$username@example.com",
    roles = "USER",
    createdAt = LocalDateTime.now(),
  )

  private fun buildRefreshToken(
    userId: Long,
    status: RefreshTokenStatus,
    fingerprint: String = "Mozilla|127.0.0.1",
    expiresAt: LocalDateTime = LocalDateTime.now().plusDays(7),
  ) = RefreshToken(
    id = 1L,
    token = "old-token-uuid",
    userId = userId,
    fingerprint = fingerprint,
    status = status,
    expiresAt = expiresAt,
  )
}
