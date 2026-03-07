package com.andver.auth.service.model

data class RegisterRequest(
  val username: String,
  val password: String,
  val email: String
)

data class LoginRequest(
  val username: String,
  val password: String
)
