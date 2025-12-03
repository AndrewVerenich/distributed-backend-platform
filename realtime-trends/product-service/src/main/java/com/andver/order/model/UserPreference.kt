package com.andver.order.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("user_preferences")
data class UserPreference(
  @Id val id: Long? = null,
  val userId: Long,
  val categoryId: Long
)
