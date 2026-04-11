package com.andver.bff.user.model

import java.time.Instant

data class UserResponse(
  val id: Long,
  val email: String,
  val name: String,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

data class UserStatsResponse(
  val totalUsers: Long,
  val activeUsers: Long,
)

data class CreateUserRequest(
  val email: String,
  val name: String,
  val createdBy: String? = null,
)

data class UpdateUserRequest(
  val email: String? = null,
  val name: String? = null,
  val isActive: Boolean? = null,
)
