package com.andver.resource.model

import java.time.LocalDateTime

data class UserInfo(
  val userId: Long,
  val username: String,
  val email: String,
  val roles: List<String>,
)

data class Order(
  val id: Long,
  val userId: Long,
  val productName: String,
  val amount: Double,
  val createdAt: LocalDateTime,
)
