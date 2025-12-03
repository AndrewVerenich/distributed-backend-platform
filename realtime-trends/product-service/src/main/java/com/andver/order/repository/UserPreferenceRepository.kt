package com.andver.order.repository

import com.andver.order.model.UserPreference
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux

interface UserPreferenceRepository : ReactiveCrudRepository<UserPreference, Long> {

  @Query("SELECT category_id FROM user_preferences WHERE user_id = :userId")
  fun findCategoriesByUserId(userId: Long): Flux<Long>
}