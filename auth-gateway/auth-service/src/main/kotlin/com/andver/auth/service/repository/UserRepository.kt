package com.andver.auth.service.repository

import com.andver.auth.service.entity.User
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface UserRepository : ReactiveCrudRepository<User, Long> {
  fun findByUsername(username: String): Mono<User>
}
