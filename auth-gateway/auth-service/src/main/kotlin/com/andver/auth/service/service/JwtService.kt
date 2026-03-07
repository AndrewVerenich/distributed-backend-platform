package com.andver.auth.service.service

import com.andver.auth.service.entity.User
import com.andver.auth.service.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
  private val jwtProperties: JwtProperties
) {
  private val log = LoggerFactory.getLogger(JwtService::class.java)
  private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))

  fun generateAccessToken(user: User): String {
    val now = Date()
    val expiresAt = Date(now.time + jwtProperties.accessExpiration.toMillis())

    return Jwts.builder()
      .subject(user.id.toString())
      .claim("username", user.username)
      .claim("roles", user.roles.split(","))
      .claim("jti", UUID.randomUUID().toString())
      .issuedAt(now)
      .expiration(expiresAt)
      .signWith(secretKey)
      .compact()
      .also { log.debug("Generated access token for userId=${user.id}") }
  }

  fun generateRefreshToken(): String {
    return UUID.randomUUID().toString()
  }

  fun extractClaims(token: String): Mono<Claims> {
    return try {
      val claims = Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .payload
      Mono.just(claims)
    } catch (e: Exception) {
      log.error("JWT parsing failed: ${e.message}")
      Mono.error(IllegalArgumentException("Invalid token"))
    }
  }
}
