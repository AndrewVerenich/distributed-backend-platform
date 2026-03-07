package com.andver.resource.controller

import com.andver.resource.model.Order
import com.andver.resource.model.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class ResourceController {
  private val log = LoggerFactory.getLogger(ResourceController::class.java)

  @GetMapping("/me")
  fun getMe(request: ServerHttpRequest): Mono<ResponseEntity<UserInfo>> {
    val (userId, username) = userIdToUserName(request)
    log.info("GET /api/me - userId=$userId, username=$username")

    return if (userId != null && username != null) {
      Mono.just(
        ResponseEntity.ok(
          UserInfo(
            userId = userId,
            username = username,
            email = "$username@example.com",
            roles = listOf("USER")
          )
        )
      )
    } else {
      Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())
    }
  }

  @GetMapping("/orders")
  fun getOrders(request: ServerHttpRequest): Mono<ResponseEntity<List<Order>>> {
    val (userId, username) = userIdToUserName(request)
    log.info("GET /api/orders - userId=$userId, username=$username")

    return if (userId != null) {
      Mono.just(
        ResponseEntity.ok(
          listOf(
            Order(1L, userId, "Order #1", 99.99, LocalDateTime.now().minusDays(5)),
            Order(2L, userId, "Order #2", 149.99, LocalDateTime.now().minusDays(2)),
            Order(3L, userId, "Order #3", 79.50, LocalDateTime.now().minusHours(3))
          )
        )
      )
    } else {
      Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build())
    }
  }

  private fun userIdToUserName(request: ServerHttpRequest): Pair<Long?, String?> {
    val userId = request.headers.getFirst("X-User-Id")?.toLongOrNull()
    val userName = request.headers.getFirst("X-Username")
    return Pair(userId, userName)
  }
}
