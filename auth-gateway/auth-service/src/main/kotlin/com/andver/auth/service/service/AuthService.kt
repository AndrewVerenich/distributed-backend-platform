package com.andver.auth.service.service

import com.andver.auth.service.entity.RefreshToken
import com.andver.auth.service.entity.RefreshTokenStatus
import com.andver.auth.service.entity.User
import com.andver.auth.service.model.RefreshResult
import com.andver.auth.service.model.TokenPair
import com.andver.auth.service.properties.JwtProperties
import com.andver.auth.service.repository.RefreshTokenRepository
import com.andver.auth.service.repository.UserRepository
import io.jsonwebtoken.Claims
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class AuthService(
  private val jwtProperties: JwtProperties,
  private val userRepository: UserRepository,
  private val refreshTokenRepository: RefreshTokenRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtService: JwtService,
  private val redisTemplate: ReactiveRedisTemplate<String, String>
) {
  private val log = LoggerFactory.getLogger(AuthService::class.java)

  fun register(username: String, password: String, email: String): Mono<User> {
    return userRepository.findByUsername(username)
      .flatMap<User> { Mono.error(IllegalArgumentException("Username already exists")) }
      .switchIfEmpty(
        userRepository.save(
          User(
            username = username,
            password = passwordEncoder.encode(password),
            email = email,
            roles = "USER"
          )
        ).doOnSuccess { log.info("User registered: username=$username, id=${it.id}") }
      )
  }

  fun login(username: String, password: String, fingerprint: String): Mono<TokenPair> {
    return userRepository.findByUsername(username)
      .switchIfEmpty(Mono.error(IllegalArgumentException("Invalid credentials")))
      .flatMap { user ->
        if (!passwordEncoder.matches(password, user.password)) {
          return@flatMap Mono.error(IllegalArgumentException("Invalid credentials"))
        }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshTokenValue = jwtService.generateRefreshToken()

        refreshTokenRepository.save(
          RefreshToken(
            token = refreshTokenValue,
            userId = user.id!!,
            fingerprint = fingerprint,
            expiresAt = LocalDateTime.now().plus(jwtProperties.refreshExpiration),
            status = RefreshTokenStatus.ACTIVE
          )
        ).map {
          log.info("User logged in: userId=${user.id}, username=$username")
          TokenPair(accessToken, refreshTokenValue)
        }
      }
  }

  fun refresh(refreshToken: String, fingerprint: String): Mono<RefreshResult> {
    return refreshTokenRepository.findByToken(refreshToken)
      .switchIfEmpty(Mono.error(IllegalArgumentException("Invalid refresh token")))
      .flatMap { token ->
        if (token.status != RefreshTokenStatus.ACTIVE) {
          log.warn("Attempted to use non-active refresh token: userId=${token.userId}, status=${token.status}")
          return@flatMap revokeTokenFamily(token.userId, token.family)
            .then(Mono.error(IllegalArgumentException("Refresh token revoked")))
        }

        if (token.expiresAt.isBefore(LocalDateTime.now())) {
          return@flatMap Mono.error(IllegalArgumentException("Refresh token expired"))
        }

        if (token.fingerprint != fingerprint) {
          log.warn("Fingerprint mismatch for refresh token: userId=${token.userId}")
          return@flatMap revokeTokenFamily(token.userId, token.family)
            .then(Mono.error(IllegalArgumentException("Security violation detected")))
        }

        userRepository.findById(token.userId)
          .switchIfEmpty(Mono.error(IllegalArgumentException("User not found")))
          .flatMap { user ->
            val newAccessToken = jwtService.generateAccessToken(user)
            val newRefreshToken = jwtService.generateRefreshToken()

            refreshTokenRepository.updateStatus(token.id!!, RefreshTokenStatus.USED)
              .then(
                refreshTokenRepository.save(
                  RefreshToken(
                    token = newRefreshToken,
                    userId = user.id!!,
                    fingerprint = fingerprint,
                    expiresAt = LocalDateTime.now().plus(jwtProperties.refreshExpiration),
                    family = token.family,
                    status = RefreshTokenStatus.ACTIVE
                  )
                )
              )
              .map {
                log.info("Refresh token rotated: userId=${user.id}")
                RefreshResult(newAccessToken, newRefreshToken)
              }
          }
      }
  }

  fun logout(refreshToken: String): Mono<Void> {
    return refreshTokenRepository.findByToken(refreshToken)
      .flatMap { token ->
        refreshTokenRepository.updateStatus(token.id!!, RefreshTokenStatus.REVOKED)
          .doOnSuccess { log.info("User logged out: userId=${token.userId}") }
      }
      .then()
  }

  fun logoutAll(accessToken: String): Mono<Void> {
    return jwtService.extractClaims(accessToken)
      .flatMap { claims ->
        val userId = claims.subject.toLong()
        refreshTokenRepository.revokeAllForUser(userId)
          .doOnSuccess { log.info("User logged out from all devices: userId=$userId") }
      }
      .then()
  }

  fun validate(accessToken: String): Mono<Claims> {
    return jwtService.extractClaims(accessToken)
      .flatMap { claims ->
        val jti = claims["jti"] as String
        redisTemplate.hasKey("blacklist:$jti")
          .flatMap { isBlacklisted ->
            if (isBlacklisted) {
              Mono.error(IllegalArgumentException("Token is blacklisted"))
            } else {
              Mono.just(claims)
            }
          }
      }
  }

  private fun revokeTokenFamily(userId: Long, family: String): Mono<Int> {
    log.warn("Revoking token family: userId=$userId, family=$family")
    return refreshTokenRepository.revokeFamily(userId, family)
  }
}
