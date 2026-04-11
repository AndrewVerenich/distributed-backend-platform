package com.andver.bff.gateway.fallback

data class FallbackErrorResponse(
  val error: String,
  val target: String,
  val message: String,
)
