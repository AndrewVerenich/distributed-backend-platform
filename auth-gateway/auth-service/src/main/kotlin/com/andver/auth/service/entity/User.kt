package com.andver.auth.service.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("users")
data class User(
  @Id
  val id: Long? = null,
  val username: String,
  val password: String,
  val email: String,
  val roles: String,
  val createdAt: LocalDateTime = LocalDateTime.now()
)
