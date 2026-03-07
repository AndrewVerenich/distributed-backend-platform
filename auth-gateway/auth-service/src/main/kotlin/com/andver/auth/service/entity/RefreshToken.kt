package com.andver.auth.service.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.*

@Table("refresh_tokens")
data class RefreshToken(
  @Id
  val id: Long? = null,
  val token: String,
  val userId: Long,
  val fingerprint: String,
  val family: String = UUID.randomUUID().toString(),
  val status: RefreshTokenStatus,
  val expiresAt: LocalDateTime,
  val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class RefreshTokenStatus {
  ACTIVE,
  USED,
  REVOKED
}
