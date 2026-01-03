package com.andver.gateway.websocket.service

import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.*

interface JwtService {
  fun validateToken(token: String): JwtValidationResult
}

data class JwtValidationResult(
  val isValid: Boolean,
  val userId: Long? = null,
  val error: String? = null
)

@Service
class DefaultJwtService(
  @Value("\${jwt.secret-key}") private val secretKey: String,
) : JwtService {

  private val keyBytes = secretKey.toByteArray(StandardCharsets.UTF_8)

  override fun validateToken(token: String): JwtValidationResult {
    return try {
      val signedJWT = SignedJWT.parse(token)

      val verifier = MACVerifier(keyBytes)
      if (!signedJWT.verify(verifier)) {
        return JwtValidationResult(isValid = false, error = "Invalid token signature")
      }

      val claimsSet = signedJWT.jwtClaimsSet

      val expirationTime = claimsSet.expirationTime
      if (expirationTime != null && expirationTime.before(Date())) {
        return JwtValidationResult(isValid = false, error = "Token expired")
      }

      val userId = extractUserIdFromClaims(claimsSet)
      if (userId == null) {
        return JwtValidationResult(isValid = false, error = "User ID not found in token")
      }

      JwtValidationResult(isValid = true, userId = userId)
    } catch (e: Exception) {
      log.warn("Failed to validate JWT token", e)
      JwtValidationResult(isValid = false, error = "Token validation failed: ${e.message}")
    }
  }

  private fun extractUserIdFromClaims(claimsSet: JWTClaimsSet): Long? {
    val userId = claimsSet.getClaim("userId")
      ?: claimsSet.getClaim("sub")
      ?: claimsSet.getClaim("user_id")
      ?: claimsSet.subject

    return when (userId) {
      is Long -> userId
      is Number -> userId.toLong()
      is String -> userId.toLongOrNull()
      else -> null
    }
  }

  private companion object {
    private val log: Logger = LogManager.getLogger()
  }
}

