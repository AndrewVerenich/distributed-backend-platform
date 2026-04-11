package com.andver.bff.web.backend

import java.time.Instant

data class UserBackendDto(
  val id: Long,
  val email: String,
  val name: String,
  val createdAt: Instant,
  val updatedAt: Instant,
  val createdBy: String?,
  val isActive: Boolean,
)

data class UserStatsBackendDto(
  val totalUsers: Long,
  val activeUsers: Long,
)
