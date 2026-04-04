package com.andver.sharding.user.repository

import com.andver.sharding.user.model.UserProfile
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface UserProfileRepository : ReactiveCrudRepository<UserProfile, UUID> {
  fun findByUserId(userId: String): Mono<UserProfile>
  fun deleteByUserId(userId: String): Mono<Long>

  @Query(
    """
    SELECT id, user_id, name, email, created_at
    FROM user_profile
    WHERE name ILIKE :q
    ORDER BY created_at DESC
    LIMIT :limit
    """,
  )
  fun searchByNameIlike(q: String, limit: Int): Flux<UserProfile>
}

