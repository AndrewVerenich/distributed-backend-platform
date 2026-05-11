package com.andver.dhm.demo.b

data class UserProfile(
  val userId: String,
  val name: String,
  val email: String,
  val tier: String = "standard",
)
