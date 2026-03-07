package com.andver.gateway.converter

import com.andver.gateway.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Component
class JwtAuthenticationConverter(
  jwtProperties: JwtProperties
) : ServerAuthenticationConverter {
  private val log = LoggerFactory.getLogger(JwtAuthenticationConverter::class.java)
  private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))

  override fun convert(exchange: ServerWebExchange): Mono<Authentication> {
    return Mono.justOrEmpty(extractToken(exchange.request))
      .flatMap { token ->
        try {
          val claims = parseClaims(token)
          val userId = claims.subject
          val username = claims["username"] as String
          val roles = (claims["roles"] as List<*>).map { SimpleGrantedAuthority("ROLE_$it") }

          log.debug("JWT authenticated: userId=$userId, username=$username")

          authToken(userId, roles, username).toMono()
        } catch (e: Exception) {
          log.error("JWT validation failed: ${e.message}")
          Mono.empty()
        }
      }
  }

  private fun authToken(
    userId: String?,
    roles: List<SimpleGrantedAuthority>,
    username: String
  ): UsernamePasswordAuthenticationToken = UsernamePasswordAuthenticationToken(
    userId,
    null,
    roles
  ).apply {
    details = mapOf(
      "username" to username,
      "userId" to userId
    )
  }

  private fun parseClaims(token: String?): Claims = Jwts.parser()
    .verifyWith(secretKey)
    .build()
    .parseSignedClaims(token)
    .payload

  private fun extractToken(request: ServerHttpRequest): String? {
    val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
    return if (authHeader?.startsWith("Bearer ") == true) {
      authHeader.substring(7)
    } else {
      null
    }
  }
}
