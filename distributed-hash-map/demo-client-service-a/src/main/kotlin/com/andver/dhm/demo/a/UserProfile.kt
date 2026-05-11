package com.andver.dhm.demo.a

data class UserProfile(
  val userId: String,
  val name: String,
  val email: String,
  val tier: String = "standard",
)
