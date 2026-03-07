package com.andver.auth.service.model

data class LoginResponse(
  val accessToken: String,
  val expiresIn: Int
)

data class RefreshResponse(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Int
)

data class MessageResponse(
  val message: String
)

data class ValidateResponse(
  val valid: Boolean,
  val userId: Long? = null,
  val username: String? = null,
  val roles: List<String>? = null
)

data class TokenPair(
  val accessToken: String,
  val refreshToken: String
)

data class RefreshResult(
  val accessToken: String,
  val newRefreshToken: String
)
