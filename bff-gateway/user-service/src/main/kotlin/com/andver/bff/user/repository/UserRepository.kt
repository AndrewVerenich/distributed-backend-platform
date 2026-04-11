package com.andver.bff.user.repository

import com.andver.bff.user.entity.AppUser
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface UserRepository : ReactiveCrudRepository<AppUser, Long> {
  fun countByIsActiveTrue(): Mono<Long>
}
