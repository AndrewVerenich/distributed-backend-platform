package com.andver.auth.service.controller

import com.andver.auth.service.model.LoginRequest
import com.andver.auth.service.model.LoginResponse
import com.andver.auth.service.model.MessageResponse
import com.andver.auth.service.model.RefreshResponse
import com.andver.auth.service.model.RegisterRequest
import com.andver.auth.service.model.ValidateResponse
import com.andver.auth.service.properties.JwtProperties
import com.andver.auth.service.service.AuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/auth")
class AuthController(
  private val authService: AuthService,
  private val jwtProperties: JwtProperties,
) {
  private val log = LoggerFactory.getLogger(AuthController::class.java)

  @PostMapping("/register")
  fun register(@RequestBody request: RegisterRequest): Mono<ResponseEntity<MessageResponse>> {
    return authService.register(request.username, request.password, request.email)
      .map { ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse("User registered successfully")) }
      .onErrorResume {
        log.error("Registration failed: ${it.message}")
        Mono.just(
          ResponseEntity.status(HttpStatus.BAD_REQUEST).body(MessageResponse(it.message ?: "Registration failed"))
        )
      }
  }

  @PostMapping("/login")
  fun login(
    @RequestBody request: LoginRequest,
    exchange: ServerWebExchange
  ): Mono<ResponseEntity<LoginResponse>> {
    return authService.login(request.username, request.password, getUserFingerprint(exchange.request))
      .map { tokens ->
        val refreshCookie = ResponseCookie
          .from("refreshToken", tokens.refreshToken)
          .httpOnly(true)
          .secure(false)
          .path("/")
          .maxAge(jwtProperties.refreshExpiration)
          .sameSite("Lax")
          .build()

        exchange.response.addCookie(refreshCookie)

        ResponseEntity.ok(
          LoginResponse(
            accessToken = tokens.accessToken,
            expiresIn = 900
          )
        )
      }
      .onErrorResume {
        log.error("Login failed: ${it.message}")
        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(LoginResponse("", 0)))
      }
  }

  @PostMapping("/refresh")
  fun refresh(
    exchange: ServerWebExchange
  ): Mono<ResponseEntity<RefreshResponse>> {
    val refreshToken = exchange.request.cookies["refreshToken"]?.firstOrNull()?.value
      ?: return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RefreshResponse("", "", 0)))

    return authService.refresh(refreshToken, getUserFingerprint(exchange.request))
      .map { tokens ->
        val newRefreshCookie = ResponseCookie
          .from("refreshToken", tokens.newRefreshToken)
          .httpOnly(true)
          .secure(false)
          .path("/")
          .maxAge(jwtProperties.refreshExpiration)
          .sameSite("Lax")
          .build()

        exchange.response.addCookie(newRefreshCookie)

        ResponseEntity.ok(
          RefreshResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.newRefreshToken,
            expiresIn = 900
          )
        )
      }
      .onErrorResume {
        log.error("Refresh failed: ${it.message}")
        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RefreshResponse("", "", 0)))
      }
  }

  @PostMapping("/logout")
  fun logout(exchange: ServerWebExchange): Mono<ResponseEntity<MessageResponse>> {
    val refreshToken = exchange.request.cookies["refreshToken"]?.firstOrNull()?.value
      ?: return Mono.just(ResponseEntity.ok(MessageResponse("Logged out")))

    return authService.logout(refreshToken)
      .then(
        Mono.fromCallable {
          val clearCookie = ResponseCookie
            .from("refreshToken", "")
            .httpOnly(true)
            .secure(false)
            .path("/")
            .maxAge(0)
            .build()

          exchange.response.addCookie(clearCookie)
          ResponseEntity.ok(MessageResponse("Logged out successfully"))
        }
      )
  }

  @PostMapping("/logout-all")
  fun logoutAll(
    @RequestHeader("Authorization") authHeader: String
  ): Mono<ResponseEntity<MessageResponse>> {
    val token = authHeader.removePrefix("Bearer ").trim()
    return authService.logoutAll(token)
      .map { ResponseEntity.ok(MessageResponse("Logged out from all devices")) }
      .onErrorResume {
        Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(MessageResponse("Invalid token")))
      }
  }

  @PostMapping("/validate")
  fun validate(@RequestHeader("Authorization") authHeader: String): Mono<ResponseEntity<ValidateResponse>> {
    val token = authHeader.removePrefix("Bearer ").trim()
    return authService.validate(token)
      .map { claims ->
        ResponseEntity.ok(
          ValidateResponse(
            valid = true,
            userId = claims.subject.toLong(),
            username = claims["username"] as String,
            roles = (claims["roles"] as List<*>).map { it.toString() }
          )
        )
      }
      .onErrorResume {
        Mono.just(ResponseEntity.ok(ValidateResponse(valid = false)))
      }
  }

  private fun getUserFingerprint(request: ServerHttpRequest): String {
    val userAgent = request.headers.getFirst("User-Agent") ?: "unknown"
    val ip = request.remoteAddress?.address?.hostAddress ?: "unknown"
    return "$userAgent|$ip"
  }
}
