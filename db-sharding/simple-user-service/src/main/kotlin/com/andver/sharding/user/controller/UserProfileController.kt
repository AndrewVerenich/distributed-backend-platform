package com.andver.sharding.user.controller

import com.andver.sharding.user.model.UserProfile
import com.andver.sharding.user.service.ShardCount
import com.andver.sharding.user.service.UserProfileService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class UserProfileController(
  private val userProfileService: UserProfileService,
) {
  @PostMapping("/users")
  fun create(@RequestBody req: CreateUserProfileRequest): Mono<UserProfile> {
    return userProfileService.create(req.userId, req.name, req.email)
  }

  @GetMapping("/users/{userId}")
  fun get(@PathVariable userId: String): Mono<UserProfile> {
    return userProfileService.get(userId)
  }

  @PutMapping("/users/{userId}")
  fun update(
    @PathVariable userId: String,
    @RequestBody req: UpdateUserProfileRequest,
  ): Mono<UserProfile> {
    return userProfileService.update(userId, req.name, req.email)
  }

  @DeleteMapping("/users/{userId}")
  fun delete(@PathVariable userId: String): Mono<Void> {
    return userProfileService.delete(userId)
  }

  @GetMapping("/users/search")
  fun searchByName(
    @RequestParam name: String,
    @RequestParam(required = false, defaultValue = "50") limit: Int
  ): Flux<UserProfile> {
    return userProfileService.searchByName(name, limit)
  }

  @GetMapping("/admin/shards")
  fun shards(): Mono<List<ShardCount>> {
    return userProfileService.countsByShard()
  }
}

data class CreateUserProfileRequest(
  val userId: String,
  val name: String,
  val email: String,
)

data class UpdateUserProfileRequest(
  val name: String,
  val email: String,
)

