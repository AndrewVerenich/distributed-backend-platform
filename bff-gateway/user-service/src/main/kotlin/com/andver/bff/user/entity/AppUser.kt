package com.andver.bff.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("app_user")
data class AppUser(
  @Id val id: Long? = null,
  val email: String,
  val name: String,
  @Column("created_at") val createdAt: Instant,
  @Column("updated_at") val updatedAt: Instant,
  @Column("created_by") val createdBy: String?,
  @Column("is_active") val isActive: Boolean,
)
