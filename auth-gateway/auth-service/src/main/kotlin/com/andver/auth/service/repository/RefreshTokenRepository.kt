package com.andver.auth.service.repository

import com.andver.auth.service.entity.RefreshToken
import com.andver.auth.service.entity.RefreshTokenStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface RefreshTokenRepository : ReactiveCrudRepository<RefreshToken, Long> {
  fun findByToken(token: String): Mono<RefreshToken>

  @Query("UPDATE refresh_tokens SET status = :status WHERE id = :id")
  fun updateStatus(id: Long, status: RefreshTokenStatus): Mono<Int>

  @Query("UPDATE refresh_tokens SET status = 'REVOKED' WHERE user_id = :userId AND status = 'ACTIVE'")
  fun revokeAllForUser(userId: Long): Mono<Int>

  @Query("UPDATE refresh_tokens SET status = 'REVOKED' WHERE user_id = :userId AND family = :family AND status = 'ACTIVE'")
  fun revokeFamily(userId: Long, family: String): Mono<Int>
}
