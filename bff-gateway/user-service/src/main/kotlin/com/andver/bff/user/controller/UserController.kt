package com.andver.bff.user.controller

import com.andver.bff.user.model.CreateUserRequest
import com.andver.bff.user.model.UpdateUserRequest
import com.andver.bff.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/users", produces = [MediaType.APPLICATION_JSON_VALUE])
class UserController(
  private val userService: UserService,
) {

  @GetMapping
  fun list() = userService.findAll()

  @GetMapping("/{id}")
  fun getById(@PathVariable id: Long) =
    userService.findById(id)
      .switchIfEmpty(Mono.error(org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND)))

  @GetMapping("/stats")
  fun stats() = userService.stats()

  @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(HttpStatus.CREATED)
  fun create(@RequestBody body: Mono<CreateUserRequest>) =
    body.flatMap { userService.create(it) }

  @PutMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun update(@PathVariable id: Long, @RequestBody body: Mono<UpdateUserRequest>) =
    body.flatMap { userService.update(id, it) }
      .switchIfEmpty(Mono.error(org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND)))
}
