package com.andver.bff.user.service

import com.andver.bff.user.entity.AppUser
import com.andver.bff.user.model.CreateUserRequest
import com.andver.bff.user.model.UpdateUserRequest
import com.andver.bff.user.model.UserResponse
import com.andver.bff.user.model.UserStatsResponse
import com.andver.bff.user.repository.UserRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.time.Instant

@Service
class UserService(
  private val userRepository: UserRepository,
) {

  fun findAll(): Flux<UserResponse> =
    userRepository.findAll().map { it.toResponse() }

  fun findById(id: Long): Mono<UserResponse> =
    userRepository.findById(id).map { it.toResponse() }

  fun create(req: CreateUserRequest): Mono<UserResponse> {
    val now = Instant.now()
    val user = AppUser(
      id = null,
      email = req.email,
      name = req.name,
      createdAt = now,
      updatedAt = now,
      createdBy = req.createdBy,
      isActive = true,
    )
    return userRepository.save(user).map { it.toResponse() }
  }

  fun update(id: Long, req: UpdateUserRequest): Mono<UserResponse> {
    return userRepository.findById(id).flatMap { existing ->
      val updated = existing.copy(
        email = req.email ?: existing.email,
        name = req.name ?: existing.name,
        isActive = req.isActive ?: existing.isActive,
        updatedAt = Instant.now(),
      )
      userRepository.save(updated).map { it.toResponse() }
    }
  }

  fun stats(): Mono<UserStatsResponse> {
    return userRepository.count()
      .zipWith(userRepository.countByIsActiveTrue())
      .map { (totalCount, activeCount) ->
        UserStatsResponse(totalUsers = totalCount, activeUsers = activeCount)
      }
  }

  private fun AppUser.toResponse() = UserResponse(
    id = id!!,
    email = email,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    isActive = isActive,
  )
}
