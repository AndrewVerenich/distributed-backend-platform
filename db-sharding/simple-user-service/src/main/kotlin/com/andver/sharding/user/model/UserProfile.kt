package com.andver.sharding.user.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

@Table("user_profile")
data class UserProfile(
  @get:JvmName("getEntityId")
  @Id
  val id: UUID,
  val userId: String,
  val name: String,
  val email: String,
  val createdAt: LocalDateTime = LocalDateTime.now(),
  @field:Transient
  private val isNewRecord: Boolean = false,
) : Persistable<UUID> {

  @PersistenceConstructor
  private constructor(
    id: UUID,
    userId: String,
    name: String,
    email: String,
    createdAt: LocalDateTime,
  ) : this(id, userId, name, email, createdAt, isNewRecord = false)

  override fun getId(): UUID = id
  override fun isNew(): Boolean = isNewRecord

  companion object {
    fun new(
      userId: String,
      name: String,
      email: String,
      id: UUID = UUID.randomUUID(),
      createdAt: LocalDateTime = LocalDateTime.now(),
    ): UserProfile =
      UserProfile(
        id = id,
        userId = userId,
        name = name,
        email = email,
        createdAt = createdAt,
        isNewRecord = true,
      )
  }
}

